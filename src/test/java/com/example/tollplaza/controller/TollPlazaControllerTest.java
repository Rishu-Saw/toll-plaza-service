package com.example.tollplaza.controller;

import com.example.tollplaza.dto.RouteResponse;
import com.example.tollplaza.dto.TollPlazaDetailsResponse;
import com.example.tollplaza.dto.TollPlazaRequest;
import com.example.tollplaza.dto.TollPlazaResponse;
import com.example.tollplaza.exception.ExternalApiException;
import com.example.tollplaza.exception.GlobalExceptionHandler;
import com.example.tollplaza.exception.InvalidPincodeException;
import com.example.tollplaza.exception.SamePincodeException;
import com.example.tollplaza.service.TollPlazaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TollPlazaController.class)
@Import(GlobalExceptionHandler.class)
class TollPlazaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TollPlazaService tollPlazaService;

    @Test
    @DisplayName("POST /api/v1/toll-plazas with valid pincodes should return 200 OK and toll plazas")
    void testFindTollPlazasSuccess() throws Exception {
        RouteResponse route = new RouteResponse("110001", "560001", 2100.0);
        List<TollPlazaDetailsResponse> tolls = List.of(
                new TollPlazaDetailsResponse("Toll Plaza 1", 28.7041, 77.1025, 200.0),
                new TollPlazaDetailsResponse("Toll Plaza 2", 19.0760, 72.8777, 1400.0)
        );
        TollPlazaResponse mockResponse = new TollPlazaResponse(route, tolls);

        when(tollPlazaService.findTollPlazas(any(TollPlazaRequest.class))).thenReturn(mockResponse);

        TollPlazaRequest request = new TollPlazaRequest("110001", "560001");

        mockMvc.perform(post("/api/v1/toll-plazas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.route.sourcePincode", is("110001")))
                .andExpect(jsonPath("$.route.destinationPincode", is("560001")))
                .andExpect(jsonPath("$.route.distanceInKm", is(2100.0)))
                .andExpect(jsonPath("$.tollPlazas", hasSize(2)))
                .andExpect(jsonPath("$.tollPlazas[0].name", is("Toll Plaza 1")))
                .andExpect(jsonPath("$.tollPlazas[0].distanceFromSource", is(200.0)))
                .andExpect(jsonPath("$.tollPlazas[1].name", is("Toll Plaza 2")))
                .andExpect(jsonPath("$.tollPlazas[1].distanceFromSource", is(1400.0)));
    }

    @Test
    @DisplayName("POST /api/v1/toll-plazas with invalid pincode should return 400 Bad Request")
    void testInvalidPincodeValidation() throws Exception {
        TollPlazaRequest request = new TollPlazaRequest("123", "560001");

        mockMvc.perform(post("/api/v1/toll-plazas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Invalid source or destination pincode")));
    }

    @Test
    @DisplayName("POST /api/v1/toll-plazas with null pincodes should return 400 Bad Request")
    void testNullPincodesValidation() throws Exception {
        TollPlazaRequest request = new TollPlazaRequest(null, null);

        mockMvc.perform(post("/api/v1/toll-plazas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Invalid source or destination pincode")));
    }

    @Test
    @DisplayName("POST /api/v1/toll-plazas with same source and destination pincode should return 400 Bad Request")
    void testSamePincodeError() throws Exception {
        when(tollPlazaService.findTollPlazas(any(TollPlazaRequest.class)))
                .thenThrow(new SamePincodeException("Source and destination pincodes cannot be the same"));

        TollPlazaRequest request = new TollPlazaRequest("110001", "110001");

        mockMvc.perform(post("/api/v1/toll-plazas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Source and destination pincodes cannot be the same")));
    }

    @Test
    @DisplayName("POST /api/v1/toll-plazas when service throws InvalidPincodeException should return 400 Bad Request")
    void testUnrecognizedPincodeError() throws Exception {
        when(tollPlazaService.findTollPlazas(any(TollPlazaRequest.class)))
                .thenThrow(new InvalidPincodeException("Invalid source or destination pincode"));

        TollPlazaRequest request = new TollPlazaRequest("999999", "560001");

        mockMvc.perform(post("/api/v1/toll-plazas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Invalid source or destination pincode")));
    }

    @Test
    @DisplayName("POST /api/v1/toll-plazas when no toll plazas found should return 200 OK with empty tollPlazas array")
    void testNoTollPlazasFound() throws Exception {
        RouteResponse route = new RouteResponse("110001", "560001", 2100.0);
        TollPlazaResponse mockResponse = new TollPlazaResponse(route, Collections.emptyList());

        when(tollPlazaService.findTollPlazas(any(TollPlazaRequest.class))).thenReturn(mockResponse);

        TollPlazaRequest request = new TollPlazaRequest("110001", "560001");

        mockMvc.perform(post("/api/v1/toll-plazas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route.sourcePincode", is("110001")))
                .andExpect(jsonPath("$.route.destinationPincode", is("560001")))
                .andExpect(jsonPath("$.tollPlazas", hasSize(0)));
    }

    @Test
    @DisplayName("POST /api/v1/toll-plazas when external API fails should return 502 Bad Gateway")
    void testExternalApiFailure() throws Exception {
        when(tollPlazaService.findTollPlazas(any(TollPlazaRequest.class)))
                .thenThrow(new ExternalApiException("Routing provider timeout"));

        TollPlazaRequest request = new TollPlazaRequest("110001", "560001");

        mockMvc.perform(post("/api/v1/toll-plazas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error", containsString("External service error")));
    }
}
