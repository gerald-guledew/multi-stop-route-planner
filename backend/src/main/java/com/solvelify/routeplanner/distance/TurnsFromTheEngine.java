package com.solvelify.routeplanner.distance;

import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import com.graphhopper.util.RoundaboutInstruction;
import com.graphhopper.util.details.PathDetail;
import com.solvelify.routeplanner.planning.Turn;
import com.solvelify.routeplanner.planning.Turn.Way;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Reads the routing engine's instructions for a drive as turns.
 *
 * <p>The engine writes sentences of its own, and they read oddly: "turn sharp right" for an
 * on-ramp and the thirteen kilometres of motorway after it, "toward" for "towards". So only what
 * it knows is kept, and the screen says it in its own words.
 *
 * <p>What it knows comes from OpenStreetMap: the name of each road, or its number, where the
 * signs point, and the names of motorway exits. A turn onto a road the map does not name, an
 * on-ramp most often, is given the road its stretch mostly follows instead, read off the names
 * the engine gives along the drive. Where the map names nothing, a turn says only which way it
 * goes.
 */
final class TurnsFromTheEngine {

    private TurnsFromTheEngine() {
    }

    /** The instructions alone, with no names along the drive to fall back on. */
    static List<Turn> from(InstructionList instructions) {
        return from(instructions, new PointList(), Map.of());
    }

    /**
     * @param points  the points of the drive. The instructions and the details count in them
     * @param details the names and the numbers of the roads along the drive, each with the points
     *                it covers, under the engine's keys for them
     */
    static List<Turn> from(InstructionList instructions, PointList points, Map<String, List<PathDetail>> details) {
        List<PathDetail> names = details.getOrDefault(Parameters.Details.STREET_NAME, List.of());
        List<PathDetail> numbers = details.getOrDefault(Parameters.Details.STREET_REF, List.of());
        List<Turn> turns = new ArrayList<>(instructions.size());
        int first = 0;
        for (int index = 0; index < instructions.size(); index++) {
            Instruction instruction = instructions.get(index);
            // Each instruction's stretch ends at the point the next one starts from.
            int last = first + instruction.getLength();
            Map<String, Object> extra = instruction.getExtraInfoJSON();
            // The engine starts every drive with a "continue", and gives it the bearing it sets
            // off on. A drive that starts inside a roundabout starts by leaving it.
            Way way = index == 0 && instruction.getSign() == Instruction.CONTINUE_ON_STREET
                    ? Way.SET_OFF
                    : wayOf(instruction.getSign());
            Optional<String> street = text(instruction.getName()).or(() -> text(extra.get("street_ref")));
            Optional<String> follows = street.isPresent() || way == Way.ARRIVE
                    ? Optional.empty()
                    : roadFollowed(names, numbers, points, first, last);
            turns.add(new Turn(
                    way,
                    street,
                    follows,
                    text(extra.get("street_destination")),
                    text(extra.get("motorway_junction")),
                    way == Way.ROUNDABOUT ? exitOf(instruction) : OptionalInt.empty(),
                    way == Way.SET_OFF ? number(extra.get("heading")) : OptionalDouble.empty(),
                    instruction.getDistance()));
            first = last;
        }
        return turns;
    }

    private static Way wayOf(int sign) {
        return switch (sign) {
            case Instruction.TURN_SLIGHT_LEFT -> Way.SLIGHT_LEFT;
            case Instruction.TURN_LEFT -> Way.LEFT;
            case Instruction.TURN_SHARP_LEFT -> Way.SHARP_LEFT;
            case Instruction.TURN_SLIGHT_RIGHT -> Way.SLIGHT_RIGHT;
            case Instruction.TURN_RIGHT -> Way.RIGHT;
            case Instruction.TURN_SHARP_RIGHT -> Way.SHARP_RIGHT;
            case Instruction.KEEP_LEFT -> Way.KEEP_LEFT;
            case Instruction.KEEP_RIGHT -> Way.KEEP_RIGHT;
            case Instruction.U_TURN_LEFT, Instruction.U_TURN_RIGHT, Instruction.U_TURN_UNKNOWN -> Way.U_TURN;
            case Instruction.USE_ROUNDABOUT -> Way.ROUNDABOUT;
            case Instruction.LEAVE_ROUNDABOUT -> Way.LEAVE_ROUNDABOUT;
            case Instruction.FERRY -> Way.FERRY;
            case Instruction.FINISH -> Way.ARRIVE;
            // Carrying on, and what a car never meets: a stop on the way, public transport.
            default -> Way.CONTINUE;
        };
    }

    /** The exit to take. None when the drive ends inside the roundabout, before any exit. */
    private static OptionalInt exitOf(Instruction instruction) {
        return instruction instanceof RoundaboutInstruction roundabout && roundabout.getExitNumber() > 0
                ? OptionalInt.of(roundabout.getExitNumber())
                : OptionalInt.empty();
    }

    /** The road a stretch mostly follows, by its name, or by its number where nothing is named. */
    private static Optional<String> roadFollowed(
            List<PathDetail> names, List<PathDetail> numbers, PointList points, int first, int last) {
        return mostOf(names, points, first, last).or(() -> mostOf(numbers, points, first, last));
    }

    /**
     * The road that covers the most of the drive between two of its points, of those the details
     * name. On a tie, the first. Empty when the details name none of it.
     */
    private static Optional<String> mostOf(List<PathDetail> details, PointList points, int first, int last) {
        Map<String, Double> metres = new LinkedHashMap<>();
        for (PathDetail detail : details) {
            Optional<String> road = text(detail.getValue());
            int from = Math.max(first, detail.getFirst());
            int to = Math.min(last, detail.getLast());
            if (road.isPresent() && from < to) {
                metres.merge(road.get(), metresBetween(points, from, to), Double::sum);
            }
        }
        return metres.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey);
    }

    private static double metresBetween(PointList points, int from, int to) {
        double metres = 0;
        for (int at = from + 1; at <= to && at < points.size(); at++) {
            metres += DistanceCalcEarth.DIST_EARTH.calcDist(
                    points.getLat(at - 1), points.getLon(at - 1), points.getLat(at), points.getLon(at));
        }
        return metres;
    }

    private static Optional<String> text(Object value) {
        return value == null || value.toString().isBlank() ? Optional.empty() : Optional.of(value.toString().strip());
    }

    private static OptionalDouble number(Object value) {
        return value instanceof Number number ? OptionalDouble.of(number.doubleValue()) : OptionalDouble.empty();
    }
}
