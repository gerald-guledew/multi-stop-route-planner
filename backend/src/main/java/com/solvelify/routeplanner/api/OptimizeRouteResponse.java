package com.solvelify.routeplanner.api;

import com.solvelify.routeplanner.planning.RoutePlan;
import java.util.List;

/**
 * The optimize response.
 *
 * <p>Rounding happens here and nowhere else. The planning code works with full precision, so
 * the rounded legs can add up to a cent less than the rounded total. That is arithmetic, not
 * a bug, and rounding earlier to hide it would make the answer slightly wrong instead.
 */
public record OptimizeRouteResponse(
        List<Place> route,
        List<Leg> legs,
        double totalDistanceKm,
        double enteredOrderDistanceKm,
        long ordersChecked) {

    public record Place(String name, double latitude, double longitude) {
    }

    public record Leg(String from, String to, double distanceKm) {
    }

    public static OptimizeRouteResponse from(RoutePlan plan) {
        return new OptimizeRouteResponse(
                plan.route().stream()
                        .map(place -> new Place(place.name(), place.latitude(), place.longitude()))
                        .toList(),
                plan.legs().stream()
                        .map(leg -> new Leg(leg.from(), leg.to(), roundToTwoDecimals(leg.distanceKm())))
                        .toList(),
                roundToTwoDecimals(plan.totalDistanceKm()),
                roundToTwoDecimals(plan.enteredOrderDistanceKm()),
                plan.ordersChecked());
    }

    private static double roundToTwoDecimals(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
