package com.solvelify.routeplanner.planning;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * One turn along a drive, as a driver follows it: which way to go, onto which road, and how far
 * to drive before the next turn. A drive's turns start with setting off and end with arriving,
 * and their distances add up to the drive.
 *
 * @param way            which way to go
 * @param street         the road the turn goes onto, by its name, or by its number when the map
 *                       gives it none. Empty when the map gives neither
 * @param follows        the road the drive after the turn mostly follows, when the turn goes onto
 *                       one the map does not name, such as a motorway's on-ramp. Empty otherwise
 * @param towards        where the signs point, on a motorway or a main road. Empty where none do
 * @param motorwayExit   the name of the motorway exit the turn takes. Empty for any other turn
 * @param roundaboutExit which exit to take, counting from where the roundabout is entered. Only
 *                       for a roundabout
 * @param heading        the compass bearing the drive sets off on, in degrees clockwise from
 *                       north. Only for setting off
 * @param metres         how far to drive after the turn, before the next one. Nought on arriving
 */
public record Turn(
        Way way,
        Optional<String> street,
        Optional<String> follows,
        Optional<String> towards,
        Optional<String> motorwayExit,
        OptionalInt roundaboutExit,
        OptionalDouble heading,
        double metres) {

    /** Which way a turn goes. */
    public enum Way {
        SET_OFF,
        CONTINUE,
        SLIGHT_LEFT,
        LEFT,
        SHARP_LEFT,
        SLIGHT_RIGHT,
        RIGHT,
        SHARP_RIGHT,
        KEEP_LEFT,
        KEEP_RIGHT,
        U_TURN,
        ROUNDABOUT,
        LEAVE_ROUNDABOUT,
        FERRY,
        ARRIVE
    }

    /** A turn with nothing said but which way it goes and what follows it. */
    public static Turn of(Way way, double metres) {
        return new Turn(way, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                OptionalInt.empty(), OptionalDouble.empty(), metres);
    }

    /** A turn onto a road known by name or number. */
    public static Turn onto(Way way, String street, double metres) {
        return new Turn(way, Optional.of(street), Optional.empty(), Optional.empty(), Optional.empty(),
                OptionalInt.empty(), OptionalDouble.empty(), metres);
    }
}
