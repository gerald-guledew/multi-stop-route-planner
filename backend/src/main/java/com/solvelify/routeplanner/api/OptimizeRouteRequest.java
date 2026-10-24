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
 *                fuel. The order is chosen for its fuel only when the least fuel is asked for
 */
public record OptimizeRouteRequest(

        @NotNull
        @Valid
        PlaceRequest start,

        @NotEmpty(message = "at least one stop is required")
        @Size(max = 10, message = "at most 10 stops are supported")
        List<@Valid PlaceRequest> stops,

        Boolean returnToStart,

        Boolean fastest,

        Boolean leastFuel,

        @Valid
        VehicleRequest vehicle) {

    /**
     * The vehicle as the plan needs it. Boxed, so a missing figure is refused rather than read
     * as nought.
     *
     * @param litresPer100Km         what the dashboard shows, or the register's figure, from 2 to 30
     * @param townLitresPer100Km     what town roads take, from 2 to 40. Left out together with the
     *                               open road's, the two are worked out from the one figure the
     *                               usual way
     * @param openRoadLitresPer100Km what the open road takes, from 2 to 40
     * @param kilograms              what the vehicle weighs empty, from 400 to 4,000. Left out,
     *                               the fuel does not count the hills
     */
    public record VehicleRequest(

            @NotNull
            @DecimalMin(value = "" + Vehicle.LEAST_LITRES_PER_100_KM, message = "must be between 2 and 30")
            @DecimalMax(value = "" + Vehicle.MOST_LITRES_PER_100_KM, message = "must be between 2 and 30")
            Double litresPer100Km,

            @DecimalMin(value = "2", message = "must be between 2 and 40")
            @DecimalMax(value = "40", message = "must be between 2 and 40")
            Double townLitresPer100Km,

            @DecimalMin(value = "2", message = "must be between 2 and 40")
            @DecimalMax(value = "40", message = "must be between 2 and 40")
            Double openRoadLitresPer100Km,

            @DecimalMin(value = "400", message = "must be between 400 and 4000")
            @DecimalMax(value = "4000", message = "must be between 400 and 4000")
            Double kilograms) {

        /** Reported as the field "townAndOpenRoad". One without the other says half of it. */
        @AssertTrue(message = "must send townLitresPer100Km and openRoadLitresPer100Km together, or neither")
        public boolean isTownAndOpenRoad() {
            return (townLitresPer100Km == null) == (openRoadLitresPer100Km == null);
        }

        FuelUse fuelUse() {
            OptionalDouble weight = kilograms == null ? OptionalDouble.empty() : OptionalDouble.of(kilograms);
            return townLitresPer100Km == null
                    ? new FuelUse(litresPer100Km, weight)
                    : new FuelUse(litresPer100Km, townLitresPer100Km, openRoadLitresPer100Km, weight);
        }
    }

    /** Defaults to a round trip, as documented in docs/api.md. */
    public boolean returnToStartOrDefault() {
        return returnToStart == null || returnToStart;
    }

    /** Whether the order is to be chosen for driving time. Left out, it is chosen for distance. */
    public boolean fastestOrDefault() {
        return fastest != null && fastest;
    }

    /**
     * Whether the order is to be chosen for the least fuel the vehicle sent takes. Left out, it is
     * chosen for distance.
     */
    public boolean leastFuelOrDefault() {
        return leastFuel != null && leastFuel;
    }

    /**
     * Reported as the field "leastFuelAlone". An order is chosen for one thing, and the fastest
     * is chosen for time.
     */
    @AssertTrue(message = "must ask for the least fuel or the fastest order, not both")
    public boolean isLeastFuelAlone() {
        return !(leastFuelOrDefault() && fastestOrDefault());
    }

    /** Reported as the field "leastFuelForAVehicle". The fuel is a vehicle's, so it is needed. */
    @AssertTrue(message = "must send the vehicle whose fuel is to be the least")
    public boolean isLeastFuelForAVehicle() {
        return !leastFuelOrDefault() || vehicle != null;
    }
}
