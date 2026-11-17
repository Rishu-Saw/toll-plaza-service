package com.example.tollplaza.service;

import com.example.tollplaza.dto.TollPlazaDetailsResponse;
import com.example.tollplaza.dto.TollPlazaRequest;
import com.example.tollplaza.dto.TollPlazaResponse;
import com.example.tollplaza.exception.InvalidPincodeException;
import com.example.tollplaza.exception.SamePincodeException;
import com.example.tollplaza.model.Coordinates;
import com.example.tollplaza.model.RouteDetails;
import com.example.tollplaza.model.TollPlaza;
import com.example.tollplaza.repository.InMemoryTollPlazaRepository;
import com.example.tollplaza.repository.TollPlazaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TollPlazaServiceTest {

    @Mock
    private GeocodingService geocodingService;

    @Mock
    private RouteService routeService;

    private TollPlazaRepository repository;
    private TollPlazaService tollPlazaService;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTollPlazaRepository();
        // 150m proximity threshold, 500m duplicate threshold
        tollPlazaService = new TollPlazaService(repository, geocodingService, routeService, 150.0, 500.0);
    }

    @Test
    @DisplayName("Should find and order toll plazas along route in ascending order of distance from source")
    void testFindTollPlazasSuccess() {
        String sourcePincode = "110001";
        String destPincode = "560001";

        Coordinates sourceCoords = new Coordinates(28.6333, 77.2167);
        Coordinates destCoords = new Coordinates(12.9716, 77.5946);

        when(geocodingService.getCoordinates("110001")).thenReturn(sourceCoords);
        when(geocodingService.getCoordinates("560001")).thenReturn(destCoords);

        // Simulated route with 3 segments
        // P0 (28.0, 77.0) -> P1 (25.0, 77.0) -> P2 (20.0, 77.0) -> P3 (15.0, 77.0)
        List<Coordinates> geometry = List.of(
                new Coordinates(28.0, 77.0),
                new Coordinates(25.0, 77.0),
                new Coordinates(20.0, 77.0),
                new Coordinates(15.0, 77.0)
        );
        RouteDetails routeDetails = new RouteDetails(sourcePincode, destPincode, 1450.0, geometry);
        when(routeService.getRoute(sourcePincode, sourceCoords, destPincode, destCoords)).thenReturn(routeDetails);

        // Add 3 toll plazas:
        // Plaza 1 at lat 24.0, lon 77.0 (along segment P1-P2, ~445 km from start)
        // Plaza 2 at lat 27.0, lon 77.0 (along segment P0-P1, ~111 km from start)
        // Plaza 3 at lat 18.0, lon 77.0 (along segment P2-P3, ~1110 km from start)
        // Plaza 4 at lat 25.0, lon 79.0 (far away, ~200km off route -> should be excluded!)
        repository.saveAll(List.of(
                new TollPlaza("Far Away Toll Plaza", 25.0, 79.0, "State"),
                new TollPlaza("Plaza Near P1", 24.0, 77.0, "State"),
                new TollPlaza("Plaza Near P0", 27.0, 77.0, "State"),
                new TollPlaza("Plaza Near P2", 18.0, 77.0, "State")
        ));

        TollPlazaRequest request = new TollPlazaRequest(sourcePincode, destPincode);
        TollPlazaResponse response = tollPlazaService.findTollPlazas(request);

        assertNotNull(response);
        assertEquals(sourcePincode, response.getRoute().getSourcePincode());
        assertEquals(destPincode, response.getRoute().getDestinationPincode());
        assertEquals(1450.0, response.getRoute().getDistanceInKm());

        List<TollPlazaDetailsResponse> tolls = response.getTollPlazas();
        assertEquals(3, tolls.size(), "Should match exactly 3 toll plazas along route");

        // Verify sorted order: Plaza Near P0 -> Plaza Near P1 -> Plaza Near P2
        assertEquals("Plaza Near P0", tolls.get(0).getName());
        assertEquals("Plaza Near P1", tolls.get(1).getName());
        assertEquals("Plaza Near P2", tolls.get(2).getName());

        assertTrue(tolls.get(0).getDistanceFromSource() < tolls.get(1).getDistanceFromSource());
        assertTrue(tolls.get(1).getDistanceFromSource() < tolls.get(2).getDistanceFromSource());
    }

    @Test
    @DisplayName("Should throw SamePincodeException when source and destination pincodes are identical")
    void testSamePincodeThrowsException() {
        TollPlazaRequest request = new TollPlazaRequest("110001", "110001");
        SamePincodeException ex = assertThrows(SamePincodeException.class, () -> tollPlazaService.findTollPlazas(request));
        assertEquals("Source and destination pincodes cannot be the same", ex.getMessage());
    }

    @Test
    @DisplayName("Should throw InvalidPincodeException when geocoding fails to resolve coordinates")
    void testInvalidPincodeThrowsException() {
        when(geocodingService.getCoordinates("999999")).thenReturn(null);

        TollPlazaRequest request = new TollPlazaRequest("999999", "560001");
        InvalidPincodeException ex = assertThrows(InvalidPincodeException.class, () -> tollPlazaService.findTollPlazas(request));
        assertEquals("Invalid source or destination pincode", ex.getMessage());
    }

    @Test
    @DisplayName("Should return route with empty tollPlazas array when no toll plazas exist on the route")
    void testNoTollPlazasFound() {
        String sourcePincode = "110001";
        String destPincode = "560001";

        Coordinates sourceCoords = new Coordinates(28.6333, 77.2167);
        Coordinates destCoords = new Coordinates(12.9716, 77.5946);

        when(geocodingService.getCoordinates("110001")).thenReturn(sourceCoords);
        when(geocodingService.getCoordinates("560001")).thenReturn(destCoords);

        List<Coordinates> geometry = List.of(
                new Coordinates(28.0, 77.0),
                new Coordinates(27.0, 77.0)
        );
        RouteDetails routeDetails = new RouteDetails(sourcePincode, destPincode, 111.0, geometry);
        when(routeService.getRoute(sourcePincode, sourceCoords, destPincode, destCoords)).thenReturn(routeDetails);

        // Repository has no tolls near (28, 77)
        repository.saveAll(List.of(
                new TollPlaza("Far Away Toll", 10.0, 75.0, "Kerala")
        ));

        TollPlazaRequest request = new TollPlazaRequest(sourcePincode, destPincode);
        TollPlazaResponse response = tollPlazaService.findTollPlazas(request);

        assertNotNull(response);
        assertEquals(sourcePincode, response.getRoute().getSourcePincode());
        assertEquals(111.0, response.getRoute().getDistanceInKm());
        assertNotNull(response.getTollPlazas());
        assertTrue(response.getTollPlazas().isEmpty(), "tollPlazas array must be empty");
    }

    @Test
    @DisplayName("Should deduplicate opposing toll booths and duplicate records")
    void testDeduplicateTollPlazas() {
        List<Coordinates> geometry = List.of(
                new Coordinates(20.0, 75.0),
                new Coordinates(20.0, 76.0)
        );
        RouteDetails routeDetails = new RouteDetails("110001", "560001", 100.0, geometry);

        // Two toll entries representing Northbound and Southbound booths 50 meters apart
        repository.saveAll(List.of(
                new TollPlaza("Sample Toll Plaza NB", 20.0001, 75.5000, "State"),
                new TollPlaza("Sample Toll Plaza SB", 20.0003, 75.5002, "State")
        ));

        List<TollPlazaDetailsResponse> result = tollPlazaService.matchTollPlazasAlongRoute(routeDetails);
        assertEquals(1, result.size(), "Should deduplicate dual carriageway booths into 1 toll plaza entry");
    }
}
