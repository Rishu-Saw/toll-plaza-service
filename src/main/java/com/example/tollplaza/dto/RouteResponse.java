package com.example.tollplaza.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Road route details between the source and destination pincodes")
public class RouteResponse {

    @Schema(description = "Source pincode", example = "110001")
    private String sourcePincode;

    @Schema(description = "Destination pincode", example = "560001")
    private String destinationPincode;

    @Schema(description = "Total road distance in kilometres", example = "2100.0")
    private Double distanceInKm;

    public RouteResponse() {
    }

    public RouteResponse(String sourcePincode, String destinationPincode, Double distanceInKm) {
        this.sourcePincode = sourcePincode;
        this.destinationPincode = destinationPincode;
        this.distanceInKm = distanceInKm;
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

    public Double getDistanceInKm() {
        return distanceInKm;
    }

    public void setDistanceInKm(Double distanceInKm) {
        this.distanceInKm = distanceInKm;
    }

    @Override
    public String toString() {
        return "RouteResponse{" +
                "sourcePincode='" + sourcePincode + '\'' +
                ", destinationPincode='" + destinationPincode + '\'' +
                ", distanceInKm=" + distanceInKm +
                '}';
    }
}
