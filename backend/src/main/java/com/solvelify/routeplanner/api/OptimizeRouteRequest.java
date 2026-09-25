package com.solvelify.routeplanner.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The optimize request.
 *
 * <p>Note the two {@code @Valid} marks. Validation does not reach inside nested objects on its
 * own, so without them a stop with latitude 500 would sail through untouched.
 */
public record OptimizeRouteRequest(

        @NotNull
        @Valid
        PlaceRequest start,

        @NotEmpty(message = "at least one stop is required")
        @Size(max = 10, message = "at most 10 stops are supported")
        List<@Valid PlaceRequest> stops,

        Boolean returnToStart) {

    /** Defaults to a round trip, as documented in docs/api.md. */
    public boolean returnToStartOrDefault() {
        return returnToStart == null || returnToStart;
    }
}
