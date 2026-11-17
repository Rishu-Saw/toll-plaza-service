package com.example.tollplaza.service;

import com.example.tollplaza.client.OpenRouteServiceClient;
import com.example.tollplaza.model.Coordinates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class OpenRouteServiceGeocodingService implements GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(OpenRouteServiceGeocodingService.class);

    private final OpenRouteServiceClient openRouteServiceClient;

    public OpenRouteServiceGeocodingService(OpenRouteServiceClient openRouteServiceClient) {
        this.openRouteServiceClient = openRouteServiceClient;
    }

    @Override
    @Cacheable(value = "geocoding", key = "#pincode", unless = "#result == null")
    public Coordinates getCoordinates(String pincode) {
        log.info("Resolving coordinates for pincode: {}", pincode);
        return openRouteServiceClient.geocode(pincode);
    }
}
