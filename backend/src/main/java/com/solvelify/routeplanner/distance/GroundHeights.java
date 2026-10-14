package com.solvelify.routeplanner.distance;

import java.util.OptionalDouble;

/**
 * How high the ground is at a point, in metres above sea level. Coordinates are WGS84 decimal
 * degrees.
 *
 * <p>Empty where nothing is known: the planner was started without heights, or the point lies
 * outside the height files on this computer. The sea is known, and reads nought.
 */
public interface GroundHeights {

    /** For a planner started without heights. */
    GroundHeights NONE = (latitude, longitude) -> OptionalDouble.empty();

    OptionalDouble at(double latitude, double longitude);
}
