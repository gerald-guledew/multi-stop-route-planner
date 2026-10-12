package com.solvelify.routeplanner.planning;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The answer: where to go in what order, how far and how long each leg is, how much it climbs,
 * and what the whole trip costs.
 *
 * <p>{@code enteredOrderDistanceKm} is the same trip driven in the order the stops arrived in.
 * It is what makes the saving visible instead of asking anyone to take it on trust.
 *
 * <p>The durations are empty when the distances are straight lines, which say how far and not
 * how long. They are driving time alone: no traffic, and nothing for the time spent at a stop.
 *
 * <p>The climb and descent are empty unless the roads were read with their heights. On a round
 * trip the two come out equal, since the trip ends where it began.
 */
public record RoutePlan(
        List<Location> route,
        List<Leg> legs,
        double totalDistanceKm,
        OptionalDouble totalDurationSeconds,
        Optional<ClimbAndDescent> totalClimbAndDescent,
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

    /** A plan from roads whose heights are not known. */
    public RoutePlan(
            List<Location> route,
            List<Leg> legs,
            double totalDistanceKm,
            OptionalDouble totalDurationSeconds,
            double enteredOrderDistanceKm,
            long ordersChecked) {
        this(route, legs, totalDistanceKm, totalDurationSeconds, Optional.empty(),
                enteredOrderDistanceKm, ordersChecked);
    }

    /**
     * @param durationSeconds how long the drive takes, empty when distances are straight lines
     * @param climbAndDescent how much the drive goes up and comes down, empty unless the roads
     *                        were read with their heights
     * @param path            the road the drive follows, empty when distances are straight lines
     */
    public record Leg(
            String from,
            String to,
            double distanceKm,
            OptionalDouble durationSeconds,
            Optional<ClimbAndDescent> climbAndDescent,
            List<GeoPoint> path) {

        /** A leg from a source that knows distances and not times. */
        public Leg(String from, String to, double distanceKm, List<GeoPoint> path) {
            this(from, to, distanceKm, OptionalDouble.empty(), path);
        }

        /** A leg along roads whose heights are not known. */
        public Leg(String from, String to, double distanceKm, OptionalDouble durationSeconds, List<GeoPoint> path) {
            this(from, to, distanceKm, durationSeconds, Optional.empty(), path);
        }
    }
}
