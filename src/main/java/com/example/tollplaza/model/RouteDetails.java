package com.example.tollplaza.model;

import java.util.ArrayList;
import java.util.List;

public class RouteDetails {

    private String sourcePincode;
    private String destinationPincode;
    private double distanceInKm;
    private List<Coordinates> geometry = new ArrayList<>();

    public RouteDetails() {
    }

    public RouteDetails(String sourcePincode, String destinationPincode, double distanceInKm, List<Coordinates> geometry) {
        this.sourcePincode = sourcePincode;
        this.destinationPincode = destinationPincode;
        this.distanceInKm = distanceInKm;
        this.geometry = geometry != null ? geometry : new ArrayList<>();
    }

    public String getSourcePincode() {
        return sourcePincode;
    }

    public void setSourcePincode(String sourcePincode) {
        this.sourcePincode = sourcePincode;
    }

    public String getDestinationPincode() {
        return destinationPincode;
    }

    public void setDestinationPincode(String destinationPincode) {
        this.destinationPincode = destinationPincode;
    }

    public double getDistanceInKm() {
        return distanceInKm;
    }

    public void setDistanceInKm(double distanceInKm) {
        this.distanceInKm = distanceInKm;
    }

    public List<Coordinates> getGeometry() {
        return geometry;
    }

    public void setGeometry(List<Coordinates> geometry) {
        this.geometry = geometry != null ? geometry : new ArrayList<>();
    }

    @Override
    public String toString() {
        return "RouteDetails{" +
                "sourcePincode='" + sourcePincode + '\'' +
                ", destinationPincode='" + destinationPincode + '\'' +
                ", distanceInKm=" + distanceInKm +
                ", geometryPoints=" + (geometry != null ? geometry.size() : 0) +
                '}';
    }
}
