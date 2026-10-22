package com.solvelify.routeplanner.planning;

import java.util.List;
import java.util.OptionalDouble;

/**
 * The fuel a plan takes, leg by leg, for one vehicle.
 *
 * <p>Worked out after the order is chosen, whatever it was chosen for, from the distance of each
 * leg and, when the roads were read with their heights and the vehicle's weight is known, from
 * its hills.
 *
 * @param legLitres           the fuel each leg takes, in the order of the plan's legs
 * @param totalLitres         the legs added up
 * @param countsHills         whether the hills were counted. Not without heights, nor without the
 *                            weight
 * @param shortestOrderLitres the fuel the shortest order takes, counted the same way. Empty when
 *                            the plan gives no shortest order to hold it against
 */
public record FuelEstimate(
        List<Double> legLitres, double totalLitres, boolean countsHills, OptionalDouble shortestOrderLitres) {

    public FuelEstimate {
        legLitres = List.copyOf(legLitres);
    }

    public static FuelEstimate of(RoutePlan plan, FuelUse fuelUse) {
        List<Double> legs = plan.legs().stream()
                .map(leg -> fuelUse.litresFor(leg.distanceKm(), leg.climbAndDescent()))
                .toList();
        boolean countsHills = fuelUse.countsHills()
                && plan.legs().stream().allMatch(leg -> leg.climbAndDescent().isPresent());
        // From the shortest order's totals. Its descents come one by one, as its legs' do, so this
        // is what its legs would add up to.
        OptionalDouble shortest = plan.shortestOrder()
                .map(order -> OptionalDouble.of(fuelUse.litresFor(order.distanceKm(), order.climbAndDescent())))
                .orElse(OptionalDouble.empty());
        return new FuelEstimate(legs, legs.stream().mapToDouble(Double::doubleValue).sum(), countsHills, shortest);
    }
}
