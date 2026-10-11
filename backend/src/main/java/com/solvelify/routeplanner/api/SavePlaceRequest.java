package com.solvelify.routeplanner.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A place to save, as it arrives over HTTP. The same three fields with the same limits as a
 * place in a trip, so whatever can be planned can be saved, and the other way round.
 */
public record SavePlaceRequest(

        @NotBlank
        @Size(max = 100, message = "must be at most 100 characters")
        String name,

        @NotNull
        @DecimalMin(value = "-90.0", message = "must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "must be between -90 and 90")
        Double latitude,

        @NotNull
        @DecimalMin(value = "-180.0", message = "must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "must be between -180 and 180")
        Double longitude) {
}
