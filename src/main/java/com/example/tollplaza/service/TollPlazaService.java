package com.example.tollplaza.service;

import com.example.tollplaza.dto.RouteResponse;
import com.example.tollplaza.dto.TollPlazaDetailsResponse;
import com.example.tollplaza.dto.TollPlazaRequest;
import com.example.tollplaza.dto.TollPlazaResponse;
import com.example.tollplaza.exception.InvalidPincodeException;
import com.example.tollplaza.exception.SamePincodeException;
import com.example.tollplaza.model.Coordinates;
import com.example.tollplaza.model.RouteDetails;
import com.example.tollplaza.model.TollPlaza;
import com.example.tollplaza.repository.TollPlazaRepository;
import com.example.tollplaza.util.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class TollPlazaService {

    private static final Logger log = LoggerFactory.getLogger(TollPlazaService.class);

    private final TollPlazaRepository tollPlazaRepository;
    private final GeocodingService geocodingService;
    private final RouteService routeService;

    private final double proximityThresholdMeters;
    private final double duplicateDistanceThresholdMeters;

    public TollPlazaService(TollPlazaRepository tollPlazaRepository,
                            GeocodingService geocodingService,
                            RouteService routeService,
                            @Value("${app.toll-matching.proximity-threshold-meters:150.0}") double proximityThresholdMeters,
                            @Value("${app.toll-matching.duplicate-distance-threshold-meters:500.0}") double duplicateDistanceThresholdMeters) {
        this.tollPlazaRepository = tollPlazaRepository;
        this.geocodingService = geocodingService;
        this.routeService = routeService;
        this.proximityThresholdMeters = proximityThresholdMeters;
        this.duplicateDistanceThresholdMeters = duplicateDistanceThresholdMeters;
    }

    @Cacheable(value = "tollPlazas", key = "#request.sourcePincode + '_' + #request.destinationPincode", unless = "#result == null")
    public TollPlazaResponse findTollPlazas(TollPlazaRequest request) {
        String sourcePincode = request.getSourcePincode() != null ? request.getSourcePincode().trim() : "";
        String destinationPincode = request.getDestinationPincode() != null ? request.getDestinationPincode().trim() : "";

        // 1. Validation
        if (sourcePincode.isEmpty() || destinationPincode.isEmpty()) {
            throw new InvalidPincodeException("Invalid source or destination pincode");
        }

        if (sourcePincode.equals(destinationPincode)) {
            throw new SamePincodeException("Source and destination pincodes cannot be the same");
        }

        log.info("Processing toll plaza request between {} and {}", sourcePincode, destinationPincode);

        // 2. Geocoding
        Coordinates sourceCoordinates = geocodingService.getCoordinates(sourcePincode);
        if (sourceCoordinates == null) {
            throw new InvalidPincodeException("Invalid source or destination pincode");
        }

        Coordinates destinationCoordinates = geocodingService.getCoordinates(destinationPincode);
        if (destinationCoordinates == null) {
            throw new InvalidPincodeException("Invalid source or destination pincode");
        }

        // 3. Road Route Generation
        RouteDetails routeDetails = routeService.getRoute(sourcePincode, sourceCoordinates, destinationPincode, destinationCoordinates);
        if (routeDetails == null || routeDetails.getGeometry() == null || routeDetails.getGeometry().isEmpty()) {
            throw new InvalidPincodeException("Unable to determine road route between given pincodes");
        }

        // 4. Geographic Matching of Toll Plazas along the route
        List<TollPlazaDetailsResponse> matchedTolls = matchTollPlazasAlongRoute(routeDetails);

        RouteResponse routeResponse = new RouteResponse(
                sourcePincode,
                destinationPincode,
                roundToTwoDecimals(routeDetails.getDistanceInKm())
        );

        return new TollPlazaResponse(routeResponse, matchedTolls);
    }

    public List<TollPlazaDetailsResponse> matchTollPlazasAlongRoute(RouteDetails routeDetails) {
        List<Coordinates> geometry = routeDetails.getGeometry();
        if (geometry.size() < 2) {
            return Collections.emptyList();
        }

        // Precompute cumulative distances along the route points in meters
        int pointCount = geometry.size();
        double[] cumulativeDistances = new double[pointCount];
        cumulativeDistances[0] = 0.0;
        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;

        for (int i = 0; i < pointCount; i++) {
            Coordinates c = geometry.get(i);
            minLat = Math.min(minLat, c.getLatitude());
            maxLat = Math.max(maxLat, c.getLatitude());
            minLon = Math.min(minLon, c.getLongitude());
            maxLon = Math.max(maxLon, c.getLongitude());

            if (i > 0) {
                cumulativeDistances[i] = cumulativeDistances[i - 1] +
                        GeoUtils.haversineDistanceMeters(geometry.get(i - 1), c);
            }
        }

        // Geographic bounding box with proximity threshold margin (~1.5 km margin)
        double marginDeg = (proximityThresholdMeters / 111000.0) + 0.015;
        double bboxMinLat = minLat - marginDeg;
        double bboxMaxLat = maxLat + marginDeg;
        double bboxMinLon = minLon - marginDeg;
        double bboxMaxLon = maxLon + marginDeg;

        List<TollPlaza> allTollPlazas = tollPlazaRepository.findAll();
        List<MatchedCandidate> candidates = new ArrayList<>();

        for (TollPlaza toll : allTollPlazas) {
            double tollLat = toll.getLatitude();
            double tollLon = toll.getLongitude();

            // Quick bounding box check
            if (tollLat < bboxMinLat || tollLat > bboxMaxLat || tollLon < bboxMinLon || tollLon > bboxMaxLon) {
                continue;
            }

            Coordinates tollCoords = new Coordinates(tollLat, tollLon);
            double minPerpDistance = Double.MAX_VALUE;
            double bestDistanceFromSourceMeters = 0.0;

            // Find closest road segment to this toll plaza
            for (int i = 0; i < pointCount - 1; i++) {
                Coordinates p1 = geometry.get(i);
                Coordinates p2 = geometry.get(i + 1);

                GeoUtils.SegmentProjection proj = GeoUtils.projectPointOnSegment(tollCoords, p1, p2);
                if (proj.getPerpendicularDistanceMeters() < minPerpDistance) {
                    minPerpDistance = proj.getPerpendicularDistanceMeters();
                    bestDistanceFromSourceMeters = cumulativeDistances[i] + proj.getDistanceAlongSegmentMeters();
                }
            }

            // Check if within configured threshold
            if (minPerpDistance <= proximityThresholdMeters) {
                double distanceKm = bestDistanceFromSourceMeters / 1000.0;
                candidates.add(new MatchedCandidate(toll, minPerpDistance, distanceKm, bestDistanceFromSourceMeters));
            }
        }

        // Sort ascending by distance from source along the route
        candidates.sort(Comparator.comparingDouble(MatchedCandidate::distanceFromSourceKm));

        // Deduplicate adjacent toll plaza entries
        List<MatchedCandidate> deduplicated = deduplicateCandidates(candidates);

        // Convert to response DTOs
        List<TollPlazaDetailsResponse> result = new ArrayList<>();
        for (MatchedCandidate candidate : deduplicated) {
            TollPlaza tp = candidate.tollPlaza();
            result.add(new TollPlazaDetailsResponse(
                    tp.getName(),
                    roundToFourDecimals(tp.getLatitude()),
                    roundToFourDecimals(tp.getLongitude()),
                    roundToTwoDecimals(candidate.distanceFromSourceKm())
            ));
        }

        log.info("Matched {} toll plazas along route of length {} km", result.size(), routeDetails.getDistanceInKm());
        return result;
    }

    private List<MatchedCandidate> deduplicateCandidates(List<MatchedCandidate> candidates) {
        if (candidates.size() <= 1) {
            return candidates;
        }

        List<MatchedCandidate> filtered = new ArrayList<>();
        for (MatchedCandidate current : candidates) {
            boolean isDuplicate = false;
            for (int i = 0; i < filtered.size(); i++) {
                MatchedCandidate existing = filtered.get(i);

                double distDiffMeters = Math.abs(current.distanceFromSourceMeters() - existing.distanceFromSourceMeters());
                boolean namesSimilar = areNamesSimilar(current.tollPlaza().getName(), existing.tollPlaza().getName());
                double coordDistMeters = GeoUtils.haversineDistanceMeters(
                        current.tollPlaza().getLatitude(), current.tollPlaza().getLongitude(),
                        existing.tollPlaza().getLatitude(), existing.tollPlaza().getLongitude()
                );

                // Precise deduplication:
                // 1. Similar/identical name AND physical distance <= 800m (e.g. spelling variants, same plaza name)
                // 2. Co-located opposing booths across highway median: physical distance <= 100m AND route distance diff <= 100m
                // Notice: Plazas with different names > 100m apart (such as ramp tolls or intersecting ring road tolls) are NEVER merged.
                boolean isSamePlaza = (namesSimilar && coordDistMeters <= 800.0) ||
                                      (coordDistMeters <= 100.0 && distDiffMeters <= 100.0);

                if (isSamePlaza) {
                    isDuplicate = true;
                    // Keep candidate closer to the road centerline
                    if (current.perpendicularDistanceMeters() < existing.perpendicularDistanceMeters()) {
                        filtered.set(i, current);
                    }
                    break;
                }
            }

            if (!isDuplicate) {
                filtered.add(current);
            }
        }

        return filtered;
    }

    private boolean areNamesSimilar(String name1, String name2) {
        if (name1 == null || name2 == null) return false;
        String n1 = normalizeName(name1);
        String n2 = normalizeName(name2);
        if (n1.isEmpty() || n2.isEmpty()) return false;
        return n1.equals(n2) ||
                n1.startsWith(n2) || n2.startsWith(n1) ||
                (n1.length() > 4 && n2.length() > 4 && (n1.contains(n2) || n2.contains(n1)));
    }

    private String normalizeName(String name) {
        String n = name.toLowerCase().replaceAll("[^a-z0-9]", "");
        for (String kw : new String[]{"tollplaza", "tollbooth", "tollway", "toll", "plaza", "booth", "nhai", "checkpost", "pl", "main"}) {
            n = n.replace(kw, "");
        }
        return n.trim();
    }

    private double roundToTwoDecimals(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private double roundToFourDecimals(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private record MatchedCandidate(
            TollPlaza tollPlaza,
            double perpendicularDistanceMeters,
            double distanceFromSourceKm,
            double distanceFromSourceMeters
    ) {}
}
