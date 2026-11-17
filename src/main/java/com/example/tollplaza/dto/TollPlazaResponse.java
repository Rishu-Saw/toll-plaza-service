package com.example.tollplaza.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "Response containing route information and list of toll plazas along the route")
public class TollPlazaResponse {

    @Schema(description = "Route details between the two pincodes")
    private RouteResponse route;

    @Schema(description = "Ordered list of toll plazas along the route, sorted by distance from source")
    private List<TollPlazaDetailsResponse> tollPlazas = new ArrayList<>();

    public TollPlazaResponse() {
    }

    public TollPlazaResponse(RouteResponse route, List<TollPlazaDetailsResponse> tollPlazas) {
        this.route = route;
        this.tollPlazas = tollPlazas != null ? tollPlazas : new ArrayList<>();
    }

    public RouteResponse getRoute() {
        return route;
    }

    public void setRoute(RouteResponse route) {
        this.route = route;
    }

    public List<TollPlazaDetailsResponse> getTollPlazas() {
        return tollPlazas;
    }

    public void setTollPlazas(List<TollPlazaDetailsResponse> tollPlazas) {
        this.tollPlazas = tollPlazas != null ? tollPlazas : new ArrayList<>();
    }

    @Override
    public String toString() {
        return "TollPlazaResponse{" +
                "route=" + route +
                ", tollPlazas=" + tollPlazas +
                '}';
    }
}
