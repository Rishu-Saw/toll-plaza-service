package com.example.tollplaza.service;

import com.example.tollplaza.model.Coordinates;

public interface GeocodingService {

    Coordinates getCoordinates(String pincode);
}
