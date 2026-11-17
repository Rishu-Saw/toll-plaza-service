package com.example.tollplaza.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Details of a single toll plaza along the route")
public class TollPlazaDetailsResponse {

    @Schema(description = "Name of the toll plaza", example = "Hulikunte Toll Plaza")
    private String name;

    @Schema(description = "Latitude coordinate of the toll plaza", example = "13.271")
    private Double latitude;

    @Schema(description = "Longitude coordinate of the toll plaza", example = "77.3476")
    private Double longitude;

    @Schema(description = "Road distance from the source pincode to this toll plaza (in km)", example = "46.09")
    private Double distanceFromSource;

    public TollPlazaDetailsResponse() {
    }

    public TollPlazaDetailsResponse(String name, Double latitude, Double longitude, Double distanceFromSource) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanceFromSource = distanceFromSource;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getDistanceFromSource() {
        return distanceFromSource;
    }

    public void setDistanceFromSource(Double distanceFromSource) {
        this.distanceFromSource = distanceFromSource;
    }

    @Override
    public String toString() {
        return "TollPlazaDetailsResponse{" +
                "name='" + name + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", distanceFromSource=" + distanceFromSource +
                '}';
    }
}
