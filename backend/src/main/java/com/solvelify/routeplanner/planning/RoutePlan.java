package com.solvelify.routeplanner.planning;

import java.util.List;
import java.util.OptionalDouble;

/**
 * The answer: where to go in what order, how far and how long each leg is, and what the whole
 * trip costs.
 *
 * <p>{@code enteredOrderDistanceKm} is the same trip driven in the order the stops arrived in.
 * It is what makes the saving visible instead of asking anyone to take it on trust.
 *
 * <p>The durations are empty when the distances are straight lines, which say how far and not
 * how long. They are driving time alone: no traffic, and nothing for the time spent at a stop.
 */
public record RoutePlan(
        List<Location> route,
        List<Leg> legs,
        double totalDistanceKm,
        OptionalDouble totalDurationSeconds,
        double enteredOrderDistanceKm,
        long ordersChecked) {

    /** A plan from a source that knows distances and not times. */
    public RoutePlan(
            List<Location> route,
            List<Leg> legs,
            double totalDistanceKm,
            double enteredOrderDistanceKm,
            long ordersChecked) {
        this(route, legs, totalDistanceKm, OptionalDouble.empty(), enteredOrderDistanceKm, ordersChecked);
    }

    /**
     * @param durationSeconds how long the drive takes, empty when distances are straight lines
     * @param path            the road the drive follows, empty when distances are straight lines
     */
    public record Leg(
            String from, String to, double distanceKm, OptionalDouble durationSeconds, List<GeoPoint> path) {

        /** A leg from a source that knows distances and not times. */
        public Leg(String from, String to, double distanceKm, List<GeoPoint> path) {
            this(from, to, distanceKm, OptionalDouble.empty(), path);
        }
    }
}
