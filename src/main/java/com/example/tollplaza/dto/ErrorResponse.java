package com.example.tollplaza.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Error response returned when the request cannot be processed")
public class ErrorResponse {

    @Schema(description = "Human-readable error message", example = "Invalid source or destination pincode")
    private String error;

    public ErrorResponse() {
    }

    public ErrorResponse(String error) {
        this.error = error;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    @Override
    public String toString() {
        return "ErrorResponse{" +
                "error='" + error + '\'' +
                '}';
    }
}
