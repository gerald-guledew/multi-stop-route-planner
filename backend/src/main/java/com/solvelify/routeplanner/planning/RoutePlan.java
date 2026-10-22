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
 *
 * <p>{@code shortestOrder} is given when the order was chosen for more than distance: for
 * distance and climb together, for driving time or for fuel. It is what the shortest order would
 * have come to, so that what was given up for what can be seen. Empty when the order was chosen
 * for distance alone: the plan is then the shortest order.
 */
public record RoutePlan(
        List<Location> route,
        List<Leg> legs,
        double totalDistanceKm,
        OptionalDouble totalDurationSeconds,
        Optional<ClimbAndDescent> totalClimbAndDescent,
        double enteredOrderDistanceKm,
        long ordersChecked,
        Optional<ShortestOrder> shortestOrder) {

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

    /** A plan chosen for distance alone. It is the shortest order itself. */
    public RoutePlan(
            List<Location> route,
            List<Leg> legs,
            double totalDistanceKm,
            OptionalDouble totalDurationSeconds,
            Optional<ClimbAndDescent> totalClimbAndDescent,
            double enteredOrderDistanceKm,
            long ordersChecked) {
        this(route, legs, totalDistanceKm, totalDurationSeconds, totalClimbAndDescent,
                enteredOrderDistanceKm, ordersChecked, Optional.empty());
    }

    /**
     * What the shortest order comes to, to hold a plan chosen for more than distance against.
     * Its time is known wherever the roads are, and its climb where they were read with their
     * heights.
     */
    public record ShortestOrder(
            double distanceKm, OptionalDouble durationSeconds, Optional<ClimbAndDescent> climbAndDescent) {
    }

    /**
     * @param durationSeconds how long the drive takes, empty when distances are straight lines
     * @param climbAndDescent how much the drive goes up and comes down, empty unless the roads
     *                        were read with their heights
     * @param path            the road the drive follows, empty when distances are straight lines
     * @param turns           the turns along that road, from setting off to arriving, empty when
     *                        distances are straight lines
     */
    public record Leg(
            String from,
            String to,
            double distanceKm,
            OptionalDouble durationSeconds,
            Optional<ClimbAndDescent> climbAndDescent,
            List<GeoPoint> path,
            List<Turn> turns) {

        public Leg {
            path = List.copyOf(path);
            turns = List.copyOf(turns);
        }

        /** A leg whose turns are not known. */
        public Leg(
                String from,
                String to,
                double distanceKm,
                OptionalDouble durationSeconds,
                Optional<ClimbAndDescent> climbAndDescent,
                List<GeoPoint> path) {
            this(from, to, distanceKm, durationSeconds, climbAndDescent, path, List.of());
        }

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
