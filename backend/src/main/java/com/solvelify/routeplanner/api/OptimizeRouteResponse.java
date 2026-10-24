package com.solvelify.routeplanner.api;

import com.solvelify.routeplanner.planning.ClimbAndDescent;
import com.solvelify.routeplanner.planning.FuelEstimate;
import com.solvelify.routeplanner.planning.GeoPoint;
import com.solvelify.routeplanner.planning.RoutePlan;
import com.solvelify.routeplanner.planning.Turn.Way;
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
 *                             for driving time or for fuel, so the two can be held against each
 *                             other. Null when the order was chosen for distance alone, and when
 *                             what it was to be chosen for is not known: times for the time,
 *                             heights and the vehicle's weight for the fuel
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
     * The shortest order: how far it is, how long it takes, how much it climbs and comes down, and
     * the fuel it takes.
     *
     * @param durationSeconds null on straight lines, as for a plan
     * @param climbMetres     null unless the roads were read with their heights
     * @param descentMetres   null whenever the climb is
     * @param fuelLitres      null when no vehicle was sent
     */
    public record ShortestOrder(
            double distanceKm, Long durationSeconds, Long climbMetres, Long descentMetres, Double fuelLitres) {
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
     * @param turns           the turns along that road, from setting off to arriving, empty when
     *                        the distances are straight lines
     */
    public record Leg(
            String from,
            String to,
            double distanceKm,
            Long durationSeconds,
            Long climbMetres,
            Long descentMetres,
            Double fuelLitres,
            double[][] path,
            List<Turn> turns) {
    }

    /**
     * One turn of a leg. What a driver is told is left to the screen.
     *
     * @param turn           which way: setOff, continue, slightLeft, left, sharpLeft, slightRight,
     *                       right, sharpRight, keepLeft, keepRight, uTurn, roundabout,
     *                       leaveRoundabout, ferry or arrive
     * @param street         the road it goes onto, by its name, or by its number when the map gives
     *                       it none. Null when the map gives neither
     * @param follows        the road the drive after it mostly follows, when it goes onto a road
     *                       the map does not name, such as a motorway's on-ramp. Null otherwise
     * @param towards        where the signs point. Null where none do
     * @param motorwayExit   the name of the motorway exit it takes. Null for any other turn
     * @param roundaboutExit which exit to take, counting from where the roundabout is entered. Null
     *                       for any other turn
     * @param heading        the bearing it sets off on, in whole degrees clockwise from north. Null
     *                       for any turn but setting off
     * @param distanceMetres how far to drive after it, before the next turn, to the metre
     */
    public record Turn(
            String turn,
            String street,
            String follows,
            String towards,
            String motorwayExit,
            Integer roundaboutExit,
            Long heading,
            long distanceMetres) {
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
                                    asPairs(leg.path()),
                                    leg.turns().stream()
                                            .map(turn -> new Turn(
                                                    wireName(turn.way()),
                                                    turn.street().orElse(null),
                                                    turn.follows().orElse(null),
                                                    turn.towards().orElse(null),
                                                    turn.motorwayExit().orElse(null),
                                                    turn.roundaboutExit().isPresent() ? turn.roundaboutExit().getAsInt() : null,
                                                    turn.heading().isPresent() ? Math.round(turn.heading().getAsDouble()) : null,
                                                    Math.round(turn.metres())))
                                            .toList());
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
                                wholeMetresDown(shortest.climbAndDescent()),
                                fuel.map(FuelEstimate::shortestOrderLitres)
                                        .filter(OptionalDouble::isPresent)
                                        .map(litres -> roundToTwoDecimals(litres.getAsDouble()))
                                        .orElse(null)))
                        .orElse(null),
                plan.ordersChecked());
    }

    private static double roundToTwoDecimals(double value) {
        return Math.round(value * 100) / 100.0;
    }

    /** Which way a turn goes, as the API names it. */
    private static String wireName(Way way) {
        return switch (way) {
            case SET_OFF -> "setOff";
            case CONTINUE -> "continue";
            case SLIGHT_LEFT -> "slightLeft";
            case LEFT -> "left";
            case SHARP_LEFT -> "sharpLeft";
            case SLIGHT_RIGHT -> "slightRight";
            case RIGHT -> "right";
            case SHARP_RIGHT -> "sharpRight";
            case KEEP_LEFT -> "keepLeft";
            case KEEP_RIGHT -> "keepRight";
            case U_TURN -> "uTurn";
            case ROUNDABOUT -> "roundabout";
            case LEAVE_ROUNDABOUT -> "leaveRoundabout";
            case FERRY -> "ferry";
            case ARRIVE -> "arrive";
        };
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
