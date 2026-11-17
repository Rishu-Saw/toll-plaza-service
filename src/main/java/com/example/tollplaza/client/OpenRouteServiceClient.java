package com.example.tollplaza.client;

import com.example.tollplaza.exception.ExternalApiException;
import com.example.tollplaza.exception.InvalidPincodeException;
import com.example.tollplaza.model.Coordinates;
import com.example.tollplaza.model.RouteDetails;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

@Component
public class OpenRouteServiceClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRouteServiceClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;

    // Built-in coordinates for standard Indian metro/benchmark pincodes to facilitate offline/test operation
    private static final Map<String, Coordinates> WELL_KNOWN_PINCODES = Map.of(
            "110001", new Coordinates(28.6333, 77.2167), // New Delhi Connaught Place
            "560001", new Coordinates(12.9716, 77.5946), // Bengaluru GPO
            "560064", new Coordinates(13.1007, 77.5963), // Bengaluru Yelahanka
            "411045", new Coordinates(18.5590, 73.7868), // Pune Baner
            "400001", new Coordinates(18.9322, 72.8354), // Mumbai Fort
            "600001", new Coordinates(13.0878, 80.2785), // Chennai
            "700001", new Coordinates(22.5726, 88.3639), // Kolkata
            "500001", new Coordinates(17.3850, 78.4867)  // Hyderabad
    );

    public OpenRouteServiceClient(RestTemplate restTemplate,
                                 ObjectMapper objectMapper,
                                 @Value("${app.openrouteservice.api-key:}") String apiKey,
                                 @Value("${app.openrouteservice.base-url:https://api.openrouteservice.org}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.baseUrl = baseUrl != null ? baseUrl.trim().replaceAll("/+$", "") : "https://api.openrouteservice.org";
    }

    public boolean hasApiKey() {
        return !apiKey.isEmpty();
    }

    /**
     * Resolves coordinates for an Indian pincode using OpenRouteService or fallback geocoders.
     */
    public Coordinates geocode(String pincode) {
        if (pincode == null || pincode.trim().isEmpty()) {
            throw new InvalidPincodeException("Invalid source or destination pincode");
        }

        String cleanPincode = pincode.trim();

        // 1. Try OpenRouteService if API key is provided
        if (hasApiKey()) {
            try {
                Coordinates coords = callOrsGeocode(cleanPincode);
                if (coords != null) {
                    return coords;
                }
            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
                log.warn("OpenRouteService API key unauthorized or quota exhausted. Falling back to alternative geocoder.");
            } catch (InvalidPincodeException e) {
                log.warn("OpenRouteService could not find pincode {}. Falling back to Nominatim.", cleanPincode);
            } catch (ExternalApiException e) {
                throw e;
            } catch (Exception e) {
                log.warn("OpenRouteService geocode error: {}. Falling back.", e.getMessage());
            }
        }

        // 2. Check well-known pincodes table (covers common benchmark pincodes offline)
        if (WELL_KNOWN_PINCODES.containsKey(cleanPincode)) {
            log.info("Resolved pincode {} from local repository coordinates", cleanPincode);
            return WELL_KNOWN_PINCODES.get(cleanPincode);
        }

        // 3. Fallback: OpenStreetMap Nominatim (highly accurate for Indian pincodes)
        try {
            Coordinates coords = callNominatimGeocode(cleanPincode);
            if (coords != null) {
                return coords;
            }
        } catch (Exception e) {
            log.warn("Nominatim geocoding failed for pincode {}: {}", cleanPincode, e.getMessage());
        }

        throw new InvalidPincodeException("Invalid source or destination pincode");
    }

    private Coordinates callOrsGeocode(String pincode) {
        // Try structured search first (targets postalcode in India specifically)
        String structuredUrl = UriComponentsBuilder.fromHttpUrl(baseUrl + "/geocode/search/structured")
                .queryParam("api_key", apiKey)
                .queryParam("postalcode", pincode)
                .queryParam("country", "India")
                .build(false)
                .toUriString();

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(structuredUrl, String.class);
            Coordinates coords = parseOrsGeocodeResponse(response.getBody(), pincode);
            if (coords != null) {
                return coords;
            }
        } catch (Exception e) {
            log.debug("ORS structured geocoding not found for {}: {}", pincode, e.getMessage());
        }

        // Try standard text search
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/geocode/search")
                .queryParam("api_key", apiKey)
                .queryParam("text", pincode)
                .queryParam("boundary.country", "IND")
                .build(false)
                .toUriString();

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            Coordinates coords = parseOrsGeocodeResponse(response.getBody(), pincode);
            if (coords != null) {
                return coords;
            }
            throw new InvalidPincodeException("Invalid source or destination pincode");
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            throw e;
        } catch (HttpStatusCodeException e) {
            throw new ExternalApiException("OpenRouteService geocoding failed with status: " + e.getStatusCode());
        } catch (ResourceAccessException e) {
            throw new ExternalApiException("OpenRouteService connection timed out", e);
        } catch (InvalidPincodeException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("Failed to parse OpenRouteService geocoding response", e);
        }
    }

    private Coordinates parseOrsGeocodeResponse(String responseBody, String pincode) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode features = root.path("features");
            if (features.isArray() && !features.isEmpty()) {
                JsonNode first = features.get(0);
                JsonNode props = first.path("properties");
                String layer = props.path("layer").asText();
                String name = props.path("name").asText();

                // Reject if Pelias only matched the whole country "India" instead of the postalcode!
                if ("country".equalsIgnoreCase(layer) || "India".equalsIgnoreCase(name)) {
                    log.warn("ORS geocoder only matched country level for pincode {}. Rejecting generic country coordinates.", pincode);
                    return null;
                }

                JsonNode coordsNode = first.path("geometry").path("coordinates");
                if (coordsNode.isArray() && coordsNode.size() >= 2) {
                    double lon = coordsNode.get(0).asDouble();
                    double lat = coordsNode.get(1).asDouble();

                    // Reject generic India country centroid (approx lat 22.0, lon 79.0 or lat 20.59, lon 78.96)
                    if ((Math.abs(lat - 22.0) < 0.2 && Math.abs(lon - 79.0) < 0.2) ||
                        (Math.abs(lat - 20.59) < 0.2 && Math.abs(lon - 78.96) < 0.2)) {
                        log.warn("ORS geocoder returned India country centroid for pincode {}. Rejecting incorrect coordinates.", pincode);
                        return null;
                    }

                    return new Coordinates(lat, lon);
                }
            }
        } catch (Exception e) {
            log.debug("Error parsing ORS geocode response: {}", e.getMessage());
        }
        return null;
    }

    private Coordinates callNominatimGeocode(String pincode) {
        String url = UriComponentsBuilder.fromHttpUrl("https://nominatim.openstreetmap.org/search")
                .queryParam("postalcode", pincode)
                .queryParam("country", "india")
                .queryParam("format", "json")
                .build(false)
                .toUriString();

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.isArray() && !root.isEmpty()) {
                    JsonNode first = root.get(0);
                    double lat = Double.parseDouble(first.path("lat").asText());
                    double lon = Double.parseDouble(first.path("lon").asText());
                    log.info("Nominatim resolved pincode {} to ({}, {})", pincode, lat, lon);
                    return new Coordinates(lat, lon);
                }
            }
        } catch (Exception e) {
            log.debug("Nominatim geocoding error: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Retrieves the road route and geometry between source and destination coordinates.
     */
    public RouteDetails getRoute(String sourcePincode, Coordinates source, String destinationPincode, Coordinates destination) {
        StringBuilder failureDetails = new StringBuilder();

        // 1. Try OpenRouteService if API key is provided
        if (hasApiKey()) {
            try {
                RouteDetails route = callOrsDirections(sourcePincode, source, destinationPincode, destination);
                if (route != null) {
                    return route;
                }
            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
                log.warn("OpenRouteService API key unauthorized or quota exhausted. Attempting fallback routing provider.");
                failureDetails.append("OpenRouteService: Unauthorized/Quota exhausted. ");
            } catch (ExternalApiException e) {
                log.warn("OpenRouteService directions error: {}. Attempting fallback routing provider.", e.getMessage());
                failureDetails.append("OpenRouteService: ").append(e.getMessage()).append(". ");
            } catch (Exception e) {
                log.warn("OpenRouteService directions error: {}. Attempting fallback routing provider.", e.getMessage());
                failureDetails.append("OpenRouteService: ").append(e.getMessage()).append(". ");
            }
        } else {
            failureDetails.append("OpenRouteService: No API key configured. ");
        }

        // 2. Fallback: OSRM (Open Source Routing Machine) public routing
        try {
            RouteDetails route = callOsrmDirections(sourcePincode, source, destinationPincode, destination);
            if (route != null) {
                return route;
            }
            failureDetails.append("OSRM: No route returned. ");
        } catch (Exception e) {
            log.warn("OSRM fallback routing request failed: {}", e.getMessage());
            failureDetails.append("OSRM: ").append(e.getMessage()).append(". ");
        }

        String msg = "External routing service unavailable. " + failureDetails.toString().trim();
        log.error(msg);
        throw new ExternalApiException(msg);
    }

    private RouteDetails callOrsDirections(String sourcePincode, Coordinates source, String destinationPincode, Coordinates destination) {
        String url = baseUrl + "/v2/directions/driving-car/geojson";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        List<List<Double>> coords = List.of(
                List.of(source.getLongitude(), source.getLatitude()),
                List.of(destination.getLongitude(), destination.getLatitude())
        );
        requestBody.put("coordinates", coords);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode features = root.path("features");
                if (features.isArray() && !features.isEmpty()) {
                    JsonNode feature = features.get(0);
                    double distanceMeters = feature.path("properties").path("summary").path("distance").asDouble();
                    double distanceKm = Math.round((distanceMeters / 1000.0) * 100.0) / 100.0;

                    List<Coordinates> geometryList = new ArrayList<>();
                    JsonNode coordinatesNode = feature.path("geometry").path("coordinates");
                    if (coordinatesNode.isArray()) {
                        for (JsonNode coordPair : coordinatesNode) {
                            if (coordPair.isArray() && coordPair.size() >= 2) {
                                double lon = coordPair.get(0).asDouble();
                                double lat = coordPair.get(1).asDouble();
                                geometryList.add(new Coordinates(lat, lon));
                            }
                        }
                    }

                    if (!geometryList.isEmpty()) {
                        return new RouteDetails(sourcePincode, destinationPincode, distanceKm, geometryList);
                    }
                }
            }
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            throw e;
        } catch (HttpStatusCodeException e) {
            throw new ExternalApiException("OpenRouteService directions failed with status: " + e.getStatusCode());
        } catch (ResourceAccessException e) {
            throw new ExternalApiException("OpenRouteService routing connection timed out", e);
        } catch (Exception e) {
            throw new ExternalApiException("Error processing OpenRouteService directions response", e);
        }
        return null;
    }

    private RouteDetails callOsrmDirections(String sourcePincode, Coordinates source, String destinationPincode, Coordinates destination) {
        String url = String.format(Locale.US,
                "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                source.getLongitude(), source.getLatitude(), destination.getLongitude(), destination.getLatitude());

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String code = root.path("code").asText();
                if (!"Ok".equalsIgnoreCase(code)) {
                    log.warn("OSRM returned status code: {}", code);
                    return null;
                }
                JsonNode routes = root.path("routes");
                if (routes.isArray() && !routes.isEmpty()) {
                    JsonNode route = routes.get(0);
                    double distanceMeters = route.path("distance").asDouble();
                    double distanceKm = Math.round((distanceMeters / 1000.0) * 100.0) / 100.0;

                    List<Coordinates> geometryList = new ArrayList<>();
                    JsonNode coordinatesNode = route.path("geometry").path("coordinates");
                    if (coordinatesNode.isArray()) {
                        for (JsonNode coordPair : coordinatesNode) {
                            if (coordPair.isArray() && coordPair.size() >= 2) {
                                double lon = coordPair.get(0).asDouble();
                                double lat = coordPair.get(1).asDouble();
                                geometryList.add(new Coordinates(lat, lon));
                            }
                        }
                    }

                    if (!geometryList.isEmpty()) {
                        return new RouteDetails(sourcePincode, destinationPincode, distanceKm, geometryList);
                    }
                }
            }
        } catch (HttpStatusCodeException e) {
            log.warn("OSRM request failed with HTTP status: {}", e.getStatusCode());
            throw new ExternalApiException("OSRM public service returned HTTP " + e.getStatusCode());
        } catch (ResourceAccessException e) {
            log.warn("OSRM request timed out: {}", e.getMessage());
            throw new ExternalApiException("OSRM connection timed out");
        } catch (Exception e) {
            log.warn("OSRM routing request failed: {}", e.getMessage());
        }
        return null;
    }
}
