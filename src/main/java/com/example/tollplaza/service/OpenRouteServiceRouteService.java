package com.example.tollplaza.service;

import com.example.tollplaza.client.OpenRouteServiceClient;
import com.example.tollplaza.model.Coordinates;
import com.example.tollplaza.model.RouteDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class OpenRouteServiceRouteService implements RouteService {

    private static final Logger log = LoggerFactory.getLogger(OpenRouteServiceRouteService.class);

    private final OpenRouteServiceClient openRouteServiceClient;

    public OpenRouteServiceRouteService(OpenRouteServiceClient openRouteServiceClient) {
        this.openRouteServiceClient = openRouteServiceClient;
    }

    @Override
    @Cacheable(value = "routes", key = "#sourcePincode + '_' + #destinationPincode", unless = "#result == null")
    public RouteDetails getRoute(String sourcePincode, Coordinates source, String destinationPincode, Coordinates destination) {
        log.info("Requesting route between {} ({}, {}) and {} ({}, {})",
                sourcePincode, source.getLatitude(), source.getLongitude(),
                destinationPincode, destination.getLatitude(), destination.getLongitude());

        return openRouteServiceClient.getRoute(sourcePincode, source, destinationPincode, destination);
    }
}
