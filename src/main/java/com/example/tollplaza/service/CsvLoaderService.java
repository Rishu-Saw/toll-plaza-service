package com.example.tollplaza.service;

import com.example.tollplaza.model.TollPlaza;
import com.example.tollplaza.repository.TollPlazaRepository;
import jakarta.annotation.PostConstruct;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CsvLoaderService {

    private static final Logger log = LoggerFactory.getLogger(CsvLoaderService.class);

    private final TollPlazaRepository tollPlazaRepository;
    private final ResourceLoader resourceLoader;
    private final String csvPath;

    public CsvLoaderService(TollPlazaRepository tollPlazaRepository,
                            ResourceLoader resourceLoader,
                            @Value("${app.csv.path:classpath:toll_plaza_india.csv}") String csvPath) {
        this.tollPlazaRepository = tollPlazaRepository;
        this.resourceLoader = resourceLoader;
        this.csvPath = csvPath;
    }

    @PostConstruct
    public void init() {
        try {
            Resource resource = resourceLoader.getResource(csvPath);
            if (!resource.exists()) {
                log.warn("Configured CSV resource '{}' does not exist. Trying fallback classpath...", csvPath);
                resource = resourceLoader.getResource("classpath:toll_plaza_india.csv");
            }
            if (resource.exists()) {
                int loaded = loadFromResource(resource);
                log.info("Successfully loaded {} toll plazas into memory from {}", loaded, resource.getDescription());
            } else {
                log.error("Could not find toll plaza CSV file at {} or fallback classpath", csvPath);
            }
        } catch (Exception e) {
            log.error("Failed to initialize toll plaza dataset from CSV: {}", e.getMessage(), e);
        }
    }

    public int loadFromResource(Resource resource) {
        try (InputStream is = resource.getInputStream()) {
            return loadFromInputStream(is);
        } catch (Exception e) {
            log.error("Failed to read CSV from resource {}: {}", resource, e.getMessage(), e);
            return 0;
        }
    }

    public int loadFromInputStream(InputStream inputStream) {
        List<TollPlaza> validPlazas = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        int recordNumber = 0;
        int malformedCount = 0;
        int duplicateCount = 0;

        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(reader, csvFormat)) {

            for (CSVRecord record : csvParser) {
                recordNumber++;
                try {
                    String name = record.isMapped("toll_name") ? record.get("toll_name") : record.get(2);
                    String latStr = record.isMapped("latitude") ? record.get("latitude") : record.get(1);
                    String lonStr = record.isMapped("longitude") ? record.get("longitude") : record.get(0);
                    String state = record.isMapped("geo_state") ? record.get("geo_state") : (record.size() > 3 ? record.get(3) : "");

                    if (name == null || name.trim().isEmpty()) {
                        log.warn("Row {}: Missing toll plaza name. Skipping.", recordNumber);
                        malformedCount++;
                        continue;
                    }

                    double lat = Double.parseDouble(latStr);
                    double lon = Double.parseDouble(lonStr);

                    if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
                        log.warn("Row {}: Coordinates out of range ({}, {}). Skipping.", recordNumber, lat, lon);
                        malformedCount++;
                        continue;
                    }

                    // Normalize key to deduplicate exact matches
                    String key = name.trim().toLowerCase() + "_" + String.format("%.5f", lat) + "_" + String.format("%.5f", lon);
                    if (seenKeys.contains(key)) {
                        duplicateCount++;
                        continue;
                    }

                    seenKeys.add(key);
                    validPlazas.add(new TollPlaza(name.trim(), lat, lon, state != null ? state.trim() : ""));

                } catch (NumberFormatException e) {
                    log.warn("Row {}: Invalid coordinate number format. Skipping. Row content: {}", recordNumber, record);
                    malformedCount++;
                } catch (Exception e) {
                    log.warn("Row {}: Error parsing record. Skipping. Reason: {}", recordNumber, e.getMessage());
                    malformedCount++;
                }
            }

            tollPlazaRepository.clear();
            tollPlazaRepository.saveAll(validPlazas);

            log.info("CSV load summary: Total rows processed: {}, Valid unique: {}, Duplicates skipped: {}, Malformed skipped: {}",
                    recordNumber, validPlazas.size(), duplicateCount, malformedCount);

            return validPlazas.size();

        } catch (Exception e) {
            log.error("Error reading CSV stream: {}", e.getMessage(), e);
            return 0;
        }
    }
}
