package com.solvelify.routeplanner.planning;

import java.util.List;

/**
 * Supplies travel figures between every pair of places.
 *
 * <p>The interface lives in the package that uses it, not the package that implements it.
 * Planning states what it needs and the distance package satisfies it, so the dependency
 * points inwards and step 3 can add a GraphHopper implementation without touching planning.
 *
 * <p>It returns the whole table rather than one pair at a time because routing engines
 * calculate many-to-many distances in a single pass. Asking pair by pair would be far slower
 * once real roads arrive.
 */
public interface TravelMatrixProvider {

    /** Every drive along the roads asked for. Straight lines are the same whichever is asked. */
    TravelMatrix matrixFor(List<Location> places, Roads roads);

    /** Every drive along the usual roads. */
    default TravelMatrix matrixFor(List<Location> places) {
        return matrixFor(places, Roads.USUAL);
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
    default Directions directionsBetween(Location from, Location to, Roads roads) {
        return Directions.NONE;
    }

    /** How to drive between two places along the usual roads. */
    default Directions directionsBetween(Location from, Location to) {
        return directionsBetween(from, to, Roads.USUAL);
    }
}
