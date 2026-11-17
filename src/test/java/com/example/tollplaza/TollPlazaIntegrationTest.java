package com.example.tollplaza;

import com.example.tollplaza.client.OpenRouteServiceClient;
import com.example.tollplaza.dto.TollPlazaRequest;
import com.example.tollplaza.dto.TollPlazaResponse;
import com.example.tollplaza.model.Coordinates;
import com.example.tollplaza.model.RouteDetails;
import com.example.tollplaza.repository.TollPlazaRepository;
import com.example.tollplaza.service.TollPlazaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class TollPlazaIntegrationTest {

    @Autowired
    private TollPlazaService tollPlazaService;

    @Autowired
    private TollPlazaRepository tollPlazaRepository;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private OpenRouteServiceClient openRouteServiceClient;

    @BeforeEach
    void clearCaches() {
        for (String name : cacheManager.getCacheNames()) {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    @Test
    @DisplayName("Application should start and load CSV toll plazas into repository")
    void testCsvDatasetLoadedAtStartup() {
        assertTrue(tollPlazaRepository.count() > 1000,
                "Repository should contain over 1000 unique toll plazas loaded from the CSV dataset");
    }

    @Test
    @DisplayName("Repeated requests for identical pincodes should hit Caffeine cache")
    void testCacheHitOnRepeatedRequests() {
        String src = "110001";
        String dst = "560001";

        Coordinates srcCoords = new Coordinates(28.6333, 77.2167);
        Coordinates dstCoords = new Coordinates(12.9716, 77.5946);

        when(openRouteServiceClient.geocode(src)).thenReturn(srcCoords);
        when(openRouteServiceClient.geocode(dst)).thenReturn(dstCoords);

        List<Coordinates> mockGeometry = List.of(
                srcCoords,
                new Coordinates(20.0, 77.0),
                dstCoords
        );
        RouteDetails route = new RouteDetails(src, dst, 2100.0, mockGeometry);
        when(openRouteServiceClient.getRoute(src, srcCoords, dst, dstCoords)).thenReturn(route);

        TollPlazaRequest request = new TollPlazaRequest(src, dst);

        // First call - should invoke client
        TollPlazaResponse first = tollPlazaService.findTollPlazas(request);
        assertNotNull(first);
        verify(openRouteServiceClient, times(1)).getRoute(src, srcCoords, dst, dstCoords);

        // Second call with same inputs - should be served from Caffeine cache
        TollPlazaResponse second = tollPlazaService.findTollPlazas(request);
        assertNotNull(second);
        assertEquals(first.getRoute().getDistanceInKm(), second.getRoute().getDistanceInKm());

        // Verify that openRouteServiceClient was NOT invoked a second time
        verify(openRouteServiceClient, times(1)).getRoute(src, srcCoords, dst, dstCoords);
    }
}
