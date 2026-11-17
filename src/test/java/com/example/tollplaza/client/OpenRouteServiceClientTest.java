package com.example.tollplaza.client;

import com.example.tollplaza.exception.ExternalApiException;
import com.example.tollplaza.exception.InvalidPincodeException;
import com.example.tollplaza.model.Coordinates;
import com.example.tollplaza.model.RouteDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenRouteServiceClientTest {

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;
    private OpenRouteServiceClient client;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        client = new OpenRouteServiceClient(restTemplate, objectMapper, "test-api-key", "https://api.openrouteservice.org");
    }

    @Test
    @DisplayName("Should successfully parse OpenRouteService geocoding response")
    void testGeocodeSuccess() {
        String jsonResponse = """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "geometry": {
                        "type": "Point",
                        "coordinates": [77.2167, 28.6333]
                      },
                      "properties": {
                        "label": "110001, New Delhi, India"
                      }
                    }
                  ]
                }
                """;

        when(restTemplate.getForEntity(contains("/geocode/search"), eq(String.class)))
                .thenReturn(new ResponseEntity<>(jsonResponse, HttpStatus.OK));

        Coordinates coords = client.geocode("110001");
        assertNotNull(coords);
        assertEquals(28.6333, coords.getLatitude(), 0.0001);
        assertEquals(77.2167, coords.getLongitude(), 0.0001);
    }

    @Test
    @DisplayName("Should successfully parse OpenRouteService directions response")
    void testDirectionsSuccess() {
        String jsonResponse = """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "properties": {
                        "summary": {
                          "distance": 2100500.0,
                          "duration": 120000.0
                        }
                      },
                      "geometry": {
                        "type": "LineString",
                        "coordinates": [
                          [77.2167, 28.6333],
                          [77.3000, 28.5000],
                          [77.5946, 12.9716]
                        ]
                      }
                    }
                  ]
                }
                """;

        when(restTemplate.exchange(contains("/v2/directions/driving-car/geojson"), any(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(jsonResponse, HttpStatus.OK));

        Coordinates src = new Coordinates(28.6333, 77.2167);
        Coordinates dst = new Coordinates(12.9716, 77.5946);

        RouteDetails route = client.getRoute("110001", src, "560001", dst);
        assertNotNull(route);
        assertEquals("110001", route.getSourcePincode());
        assertEquals("560001", route.getDestinationPincode());
        assertEquals(2100.5, route.getDistanceInKm(), 0.1);
        assertEquals(3, route.getGeometry().size());
        assertEquals(28.6333, route.getGeometry().get(0).getLatitude(), 0.0001);
        assertEquals(77.2167, route.getGeometry().get(0).getLongitude(), 0.0001);
    }

    @Test
    @DisplayName("Should throw InvalidPincodeException when pincode is not found")
    void testGeocodeNotFoundThrowsException() {
        String emptyFeatures = """
                {
                  "type": "FeatureCollection",
                  "features": []
                }
                """;

        when(restTemplate.getForEntity(contains("/geocode/search"), eq(String.class)))
                .thenReturn(new ResponseEntity<>(emptyFeatures, HttpStatus.OK));

        // For unknown pincode (not in well-known offline list)
        assertThrows(InvalidPincodeException.class, () -> client.geocode("987654"));
    }

    @Test
    @DisplayName("Should handle 500 server error from routing service")
    void testRoutingServiceError() {
        when(restTemplate.exchange(contains("/v2/directions/driving-car/geojson"), any(), any(), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        Coordinates src = new Coordinates(28.6333, 77.2167);
        Coordinates dst = new Coordinates(12.9716, 77.5946);

        assertThrows(ExternalApiException.class, () -> client.getRoute("110001", src, "560001", dst));
    }
}
