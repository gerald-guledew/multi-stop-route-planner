package com.solvelify.routeplanner.planning;

import java.util.List;

/**
 * The fuel a plan takes, leg by leg, for one vehicle.
 *
 * <p>Worked out after the order is chosen, from the distance of each leg. The order is still
 * chosen for distance, or for distance and climb together.
 *
 * @param legLitres   the fuel each leg takes, in the order of the plan's legs
 * @param totalLitres the legs added up
 */
public record FuelEstimate(List<Double> legLitres, double totalLitres) {

    public FuelEstimate {
        legLitres = List.copyOf(legLitres);
    }

    public static FuelEstimate of(RoutePlan plan, FuelUse fuelUse) {
        List<Double> legs = plan.legs().stream()
                .map(leg -> fuelUse.litresFor(leg.distanceKm()))
                .toList();
        return new FuelEstimate(legs, legs.stream().mapToDouble(Double::doubleValue).sum());
    }
}
