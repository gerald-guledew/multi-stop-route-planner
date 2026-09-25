package com.solvelify.routeplanner.planning;

import java.util.List;

/**
 * The answer: where to go in what order, how far each leg is, and what the whole trip costs.
 *
 * <p>{@code enteredOrderDistanceKm} is the same trip driven in the order the stops arrived in.
 * It is what makes the saving visible instead of asking anyone to take it on trust.
 */
public record RoutePlan(
        List<Location> route,
        List<Leg> legs,
        double totalDistanceKm,
        double enteredOrderDistanceKm,
        long ordersChecked) {

    public record Leg(String from, String to, double distanceKm) {
    }
}
