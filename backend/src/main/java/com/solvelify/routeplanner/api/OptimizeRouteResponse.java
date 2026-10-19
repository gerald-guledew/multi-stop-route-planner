package com.solvelify.routeplanner.api;

import com.solvelify.routeplanner.planning.ClimbAndDescent;
import com.solvelify.routeplanner.planning.FuelEstimate;
import com.solvelify.routeplanner.planning.GeoPoint;
import com.solvelify.routeplanner.planning.RoutePlan;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.IntStream;

/**
 * The optimize response.
 *
 * <p>Rounding happens here and nowhere else. The planning code works with full precision, so
 * the rounded legs can add up to a cent less than the rounded total. That is arithmetic, not
 * a bug, and rounding earlier to hide it would make the answer slightly wrong instead.
 *
 * @param totalDurationSeconds driving time for the whole trip, without traffic and without the
 *                             time spent at each stop. Null when distances are straight lines,
 *                             which say how far and not how long
 * @param totalClimbMetres     every metre the trip goes up, added along the road. Null unless the
 *                             roads were read with their heights
 * @param totalDescentMetres   every metre the trip comes down, as a number above zero. Null
 *                             whenever the climb is
 * @param totalFuelLitres      the fuel the trip takes, for the vehicle sent with the request.
 *                             Null when none was sent
 * @param fuelCountsHills      whether the fuel counts the hills. False when the roads were read
 *                             without heights or the vehicle came without its weight. Null when
 *                             no vehicle was sent
 * @param shortestOrder        what the shortest order comes to, given when the order was chosen
 *                             for distance and climb together or for driving time, so the two
 *                             can be held against each other. Null when the order was chosen for
 *                             distance alone, and when what it was to be chosen for is not known:
 *                             heights for the climb, times for the time
 */
public record OptimizeRouteResponse(
        List<Place> route,
        List<Leg> legs,
        double totalDistanceKm,
        Long totalDurationSeconds,
        Long totalClimbMetres,
        Long totalDescentMetres,
        Double totalFuelLitres,
        Boolean fuelCountsHills,
        double enteredOrderDistanceKm,
        ShortestOrder shortestOrder,
        long ordersChecked) {

    public record Place(String name, double latitude, double longitude) {
    }

    /**
     * The shortest order: how far it is, how long it takes, and how much it climbs and comes down.
     *
     * @param durationSeconds null on straight lines, as for a plan
     * @param climbMetres     null unless the roads were read with their heights
     * @param descentMetres   null whenever the climb is
     */
    public record ShortestOrder(double distanceKm, Long durationSeconds, Long climbMetres, Long descentMetres) {
    }

    /**
     * @param durationSeconds driving time for this leg, null when distances are straight lines
     * @param climbMetres     how much this leg goes up, null unless the roads were read with
     *                        their heights
     * @param descentMetres   how much this leg comes down, null whenever the climb is
     * @param fuelLitres      the fuel this leg takes, null when no vehicle was sent
     * @param path            the road this leg follows, as [latitude, longitude] pairs, empty when
     *                        the distances are straight lines. Pairs rather than objects because a
     *                        single leg can carry hundreds of points, and field names would triple
     *                        the payload.
     */
    public record Leg(
            String from,
            String to,
            double distanceKm,
            Long durationSeconds,
            Long climbMetres,
            Long descentMetres,
            Double fuelLitres,
            double[][] path) {
    }

    public static OptimizeRouteResponse from(RoutePlan plan) {
        return from(plan, Optional.empty());
    }

    /** @param fuel the fuel each leg takes, for the vehicle sent. Empty when none was sent */
    public static OptimizeRouteResponse from(RoutePlan plan, Optional<FuelEstimate> fuel) {
        return new OptimizeRouteResponse(
                plan.route().stream()
                        .map(place -> new Place(place.name(), place.latitude(), place.longitude()))
                        .toList(),
                IntStream.range(0, plan.legs().size())
                        .mapToObj(index -> {
                            RoutePlan.Leg leg = plan.legs().get(index);
                            return new Leg(
                                    leg.from(),
                                    leg.to(),
                                    roundToTwoDecimals(leg.distanceKm()),
                                    wholeSeconds(leg.durationSeconds()),
                                    wholeMetresUp(leg.climbAndDescent()),
                                    wholeMetresDown(leg.climbAndDescent()),
                                    fuel.map(litres -> roundToTwoDecimals(litres.legLitres().get(index))).orElse(null),
                                    asPairs(leg.path()));
                        })
                        .toList(),
                roundToTwoDecimals(plan.totalDistanceKm()),
                wholeSeconds(plan.totalDurationSeconds()),
                wholeMetresUp(plan.totalClimbAndDescent()),
                wholeMetresDown(plan.totalClimbAndDescent()),
                fuel.map(litres -> roundToTwoDecimals(litres.totalLitres())).orElse(null),
                fuel.map(FuelEstimate::countsHills).orElse(null),
                roundToTwoDecimals(plan.enteredOrderDistanceKm()),
                plan.shortestOrder()
                        .map(shortest -> new ShortestOrder(
                                roundToTwoDecimals(shortest.distanceKm()),
                                wholeSeconds(shortest.durationSeconds()),
                                wholeMetresUp(shortest.climbAndDescent()),
                                wholeMetresDown(shortest.climbAndDescent())))
                        .orElse(null),
                plan.ordersChecked());
    }

    private static double roundToTwoDecimals(double value) {
        return Math.round(value * 100) / 100.0;
    }

    /** To the second. Null, and not zero, when there is no time to give: zero would be a claim. */
    private static Long wholeSeconds(OptionalDouble seconds) {
        return seconds.isPresent() ? Math.round(seconds.getAsDouble()) : null;
    }

    /**
     * To the metre, which is already finer than the heights behind it. Null when the roads were
     * read without heights. Nought is an answer of its own: a level drive.
     */
    private static Long wholeMetresUp(Optional<ClimbAndDescent> known) {
        return known.map(drive -> Math.round(drive.climbMetres())).orElse(null);
    }

    private static Long wholeMetresDown(Optional<ClimbAndDescent> known) {
        return known.map(drive -> Math.round(drive.descentMetres())).orElse(null);
    }

    /**
     * Five decimal places is about a metre, which is far finer than a line drawn on a map needs
     * and noticeably smaller to send than GraphHopper's full precision.
     */
    private static double[][] asPairs(List<GeoPoint> path) {
        return path.stream()
                .map(point -> new double[] {
                        roundToFiveDecimals(point.latitude()),
                        roundToFiveDecimals(point.longitude())})
                .toArray(double[][]::new);
    }

    private static double roundToFiveDecimals(double value) {
        return Math.round(value * 100_000) / 100_000.0;
    }
}
