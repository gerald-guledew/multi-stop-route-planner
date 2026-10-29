package com.solvelify.routeplanner.planning;

import java.util.List;

/**
 * Supplies travel figures between every pair of places.
 *
 * <p>The interface lives in the package that uses it, not the package that implements it.
 * Planning states what it needs and the distance package satisfies it, so the dependency
 * points inwards and step 3 added a GraphHopper implementation without touching planning.
 *
 * <p>It returns the whole table rather than one pair at a time, so an engine that works out
 * many places to many in one pass can fill it that way. The GraphHopper implementation fills it
 * one drive at a time.
 */
public interface TravelMatrixProvider {

    /** Every drive between the places, both ways. */
    TravelMatrix matrixFor(List<Location> places);

    /**
     * One drive, for when a few are wanted rather than every pair: what a stop on the way adds
     * to a leg, say. Worked out here as a table of the two places, which costs the drive back too.
     * A provider for which every drive costs something works out the one.
     */
    default Drive driveBetween(Location from, Location to) {
        TravelMatrix matrix = matrixFor(List.of(from, to));
        return new Drive(matrix.distanceKm(0, 1), matrix.durationSeconds(0, 1));
    }

    /**
     * How to drive between two places: the shape of the road, for drawing on a map, and its
     * turns, for following it.
     *
     * <p>Optional because it is meaningless for straight lines: two points already describe
     * them, and they have no turns. A provider that knows roads returns the points the road
     * actually follows, and the map can then show the drive instead of a line through the
     * harbour.
     *
     * <p>Asked for only on the legs that end up in the answer, rather than for every pair in
     * the matrix, because a plan of n stops uses n legs out of n squared possibilities.
     */
    default Directions directionsBetween(Location from, Location to) {
        return Directions.NONE;
    }
}
