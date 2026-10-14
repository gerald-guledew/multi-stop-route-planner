package com.solvelify.routeplanner.api;

import com.solvelify.routeplanner.distance.GroundHeights;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.util.OptionalDouble;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * How high the ground is where a pin stands. The map screen asks when a pin's popup opens.
 *
 * <p>A POST and not a GET, so the exact position travels in the body. A URL is what logs keep,
 * and a pin is often somebody's own front door. Search rounds its {@code near} for the same
 * reason, and a height cannot be rounded that far.
 */
@RestController
@RequestMapping("/api/v1/heights")
public class HeightController {

    private final GroundHeights groundHeights;

    public HeightController(GroundHeights groundHeights) {
        this.groundHeights = groundHeights;
    }

    @PostMapping("/lookup")
    public HeightResult lookUp(@Valid @RequestBody HeightRequest request) {
        OptionalDouble height = groundHeights.at(request.latitude(), request.longitude());
        return new HeightResult(height.isPresent() ? Math.round(height.getAsDouble()) : null);
    }

    /** Boxed, so a missing field is refused rather than read as nought, a real position in the Atlantic. */
    public record HeightRequest(

            @NotNull
            @DecimalMin(value = "-90.0", message = "must be between -90 and 90")
            @DecimalMax(value = "90.0", message = "must be between -90 and 90")
            Double latitude,

            @NotNull
            @DecimalMin(value = "-180.0", message = "must be between -180 and 180")
            @DecimalMax(value = "180.0", message = "must be between -180 and 180")
            Double longitude) {
    }

    /**
     * @param heightMetres whole metres above sea level, below it when negative. Null, and not
     *                     nought, where nothing is known: nought is the sea
     */
    public record HeightResult(Long heightMetres) {
    }
}
