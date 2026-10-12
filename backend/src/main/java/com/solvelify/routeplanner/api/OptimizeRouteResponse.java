package com.solvelify.routeplanner.api;

import com.solvelify.routeplanner.planning.GeoPoint;
import com.solvelify.routeplanner.planning.RoutePlan;
import java.util.List;
import java.util.OptionalDouble;

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
 */
public record OptimizeRouteResponse(
        List<Place> route,
        List<Leg> legs,
        double totalDistanceKm,
        Long totalDurationSeconds,
        double enteredOrderDistanceKm,
        long ordersChecked) {

    public record Place(String name, double latitude, double longitude) {
    }

    /**
     * @param durationSeconds driving time for this leg, null when distances are straight lines
     * @param path            the road this leg follows, as [latitude, longitude] pairs, empty when
     *                        the distances are straight lines. Pairs rather than objects because a
     *                        single leg can carry hundreds of points, and field names would triple
     *                        the payload.
     */
    public record Leg(String from, String to, double distanceKm, Long durationSeconds, double[][] path) {
    }

    public static OptimizeRouteResponse from(RoutePlan plan) {
        return new OptimizeRouteResponse(
                plan.route().stream()
                        .map(place -> new Place(place.name(), place.latitude(), place.longitude()))
                        .toList(),
                plan.legs().stream()
                        .map(leg -> new Leg(
                                leg.from(),
                                leg.to(),
                                roundToTwoDecimals(leg.distanceKm()),
                                wholeSeconds(leg.durationSeconds()),
                                asPairs(leg.path())))
                        .toList(),
                roundToTwoDecimals(plan.totalDistanceKm()),
                wholeSeconds(plan.totalDurationSeconds()),
                roundToTwoDecimals(plan.enteredOrderDistanceKm()),
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
