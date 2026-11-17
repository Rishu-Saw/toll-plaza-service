package com.example.tollplaza.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Request body containing the source and destination Indian pincodes")
public class TollPlazaRequest {

    @Schema(description = "6-digit Indian source pincode", example = "110001", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Invalid source or destination pincode")
    @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Invalid source or destination pincode")
    private String sourcePincode;

    @Schema(description = "6-digit Indian destination pincode", example = "560001", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Invalid source or destination pincode")
    @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Invalid source or destination pincode")
    private String destinationPincode;

    public TollPlazaRequest() {
    }

    public TollPlazaRequest(String sourcePincode, String destinationPincode) {
        this.sourcePincode = sourcePincode;
        this.destinationPincode = destinationPincode;
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

    @Override
    public String toString() {
        return "TollPlazaRequest{" +
                "sourcePincode='" + sourcePincode + '\'' +
                ", destinationPincode='" + destinationPincode + '\'' +
                '}';
    }
}
