package com.example.tollplaza.controller;

import com.example.tollplaza.dto.ErrorResponse;
import com.example.tollplaza.dto.TollPlazaRequest;
import com.example.tollplaza.dto.TollPlazaResponse;
import com.example.tollplaza.service.TollPlazaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/toll-plazas")
@Tag(name = "Toll Plaza API", description = "Endpoints for finding and calculating toll plazas along Indian road routes")
public class TollPlazaController {

    private static final Logger log = LoggerFactory.getLogger(TollPlazaController.class);

    private final TollPlazaService tollPlazaService;

    public TollPlazaController(TollPlazaService tollPlazaService) {
        this.tollPlazaService = tollPlazaService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Find Toll Plazas Between Two Pincodes",
            description = "Accepts a source and destination Indian pincode, generates the road route, and returns details of all toll plazas located along the route sorted by distance from source."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successful route and toll plaza retrieval (or empty list if no tolls found)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = TollPlazaResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid pincode or same source and destination pincode",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "External routing or geocoding service unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<TollPlazaResponse> getTollPlazasBetweenPincodes(@Valid @RequestBody TollPlazaRequest request) {
        log.info("Received request for toll plazas between {} and {}", request.getSourcePincode(), request.getDestinationPincode());
        TollPlazaResponse response = tollPlazaService.findTollPlazas(request);
        return ResponseEntity.ok(response);
    }
}
