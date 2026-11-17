package com.example.tollplaza.service;

import com.example.tollplaza.model.TollPlaza;
import com.example.tollplaza.repository.InMemoryTollPlazaRepository;
import com.example.tollplaza.repository.TollPlazaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvLoaderServiceTest {

    private TollPlazaRepository repository;
    private CsvLoaderService csvLoaderService;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTollPlazaRepository();
        csvLoaderService = new CsvLoaderService(repository, new DefaultResourceLoader(), "classpath:test.csv");
    }

    @Test
    @DisplayName("Should successfully load valid CSV records")
    void testLoadValidRecords() {
        String csvContent = """
                longitude,latitude,toll_name,geo_state
                77.1025,28.7041,Toll Plaza 1,Delhi
                72.8777,19.0760,Toll Plaza 2,Maharashtra
                """;

        int loaded = csvLoaderService.loadFromInputStream(new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8)));
        assertEquals(2, loaded);
        assertEquals(2, repository.count());

        List<TollPlaza> plazas = repository.findAll();
        assertEquals("Toll Plaza 1", plazas.get(0).getName());
        assertEquals(28.7041, plazas.get(0).getLatitude(), 0.0001);
        assertEquals(77.1025, plazas.get(0).getLongitude(), 0.0001);
        assertEquals("Delhi", plazas.get(0).getState());
    }

    @Test
    @DisplayName("Should skip malformed records with invalid coordinates or missing names")
    void testSkipMalformedRecords() {
        String csvContent = """
                longitude,latitude,toll_name,geo_state
                invalid_lon,28.7041,Toll Plaza 1,Delhi
                72.8777,invalid_lat,Toll Plaza 2,Maharashtra
                ,,Toll Plaza 3,Karnataka
                77.5946,12.9716,,Karnataka
                200.0,28.7041,Out Of Range Lon,Delhi
                77.1025,95.0,Out Of Range Lat,Delhi
                75.2797,14.9100,Valid Plaza,Karnataka
                """;

        int loaded = csvLoaderService.loadFromInputStream(new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, loaded);
        assertEquals(1, repository.count());
        assertEquals("Valid Plaza", repository.findAll().get(0).getName());
    }

    @Test
    @DisplayName("Should skip duplicate records with same name and coordinates")
    void testSkipDuplicateRecords() {
        String csvContent = """
                longitude,latitude,toll_name,geo_state
                75.2797879,14.9100198,Bankapur Toll Plaza,Karnataka
                75.2797879,14.9100198,Bankapur Toll Plaza,Karnataka
                75.2797879,14.9100198,bankapur toll plaza,Karnataka
                """;

        int loaded = csvLoaderService.loadFromInputStream(new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, loaded);
        assertEquals(1, repository.count());
    }
}
