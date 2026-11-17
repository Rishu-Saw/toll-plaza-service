package com.example.tollplaza.service;

import com.example.tollplaza.model.Coordinates;
import com.example.tollplaza.model.RouteDetails;

public interface RouteService {

    RouteDetails getRoute(String sourcePincode, Coordinates source, String destinationPincode, Coordinates destination);
}
