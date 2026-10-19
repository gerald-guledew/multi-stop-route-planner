package com.solvelify.routeplanner.api;

import com.solvelify.routeplanner.planning.FuelUse;
import com.solvelify.routeplanner.vehicle.Vehicle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.OptionalDouble;

/**
 * The optimize request.
 *
 * <p>Note the {@code @Valid} marks. Validation does not reach inside nested objects on its
 * own, so without them a stop with latitude 500 would sail through untouched.
 *
 * @param vehicle the vehicle to work out the fuel for. Left out, the plan says nothing about
 *                fuel. The order is chosen the same way either way
 */
public record OptimizeRouteRequest(

        @NotNull
        @Valid
        PlaceRequest start,

        @NotEmpty(message = "at least one stop is required")
        @Size(max = 10, message = "at most 10 stops are supported")
        List<@Valid PlaceRequest> stops,

        Boolean returnToStart,

        Boolean factorInRoadClimbs,

        Boolean fastest,

        @Valid
        VehicleRequest vehicle) {

    /**
     * The vehicle as the plan needs it. Boxed, so a missing figure is refused rather than read
     * as nought.
     *
     * @param litresPer100Km what the dashboard shows, or the register's figure, from 2 to 30
     * @param kilograms      what the vehicle weighs empty, from 400 to 4,000. Left out, the
     *                       fuel does not count the hills
     */
    public record VehicleRequest(

            @NotNull
            @DecimalMin(value = "" + Vehicle.LEAST_LITRES_PER_100_KM, message = "must be between 2 and 30")
            @DecimalMax(value = "" + Vehicle.MOST_LITRES_PER_100_KM, message = "must be between 2 and 30")
            Double litresPer100Km,

            @DecimalMin(value = "400", message = "must be between 400 and 4000")
            @DecimalMax(value = "4000", message = "must be between 400 and 4000")
            Double kilograms) {

        FuelUse fuelUse() {
            return new FuelUse(litresPer100Km,
                    kilograms == null ? OptionalDouble.empty() : OptionalDouble.of(kilograms));
        }
    }

    /** Defaults to a round trip, as documented in docs/api.md. */
    public boolean returnToStartOrDefault() {
        return returnToStart == null || returnToStart;
    }

    /**
     * Whether the order is to be chosen for distance and climb together. Left out, it is chosen
     * for distance alone.
     */
    public boolean factorInRoadClimbsOrDefault() {
        return factorInRoadClimbs != null && factorInRoadClimbs;
    }

    /** Whether the order is to be chosen for driving time. Left out, it is chosen for distance. */
    public boolean fastestOrDefault() {
        return fastest != null && fastest;
    }

    /**
     * Reported as the field "fastestOrRoadClimbs". Road climbs are counted as road, which the
     * shortest order is chosen for and the fastest is not.
     */
    @AssertTrue(message = "must ask for the fastest order or for road climbs factored in, not both")
    public boolean isFastestOrRoadClimbs() {
        return !(fastestOrDefault() && factorInRoadClimbsOrDefault());
    }
}
