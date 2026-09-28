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

    TravelMatrix matrixFor(List<Location> places);

    /**
     * The shape of the drive between two places, for drawing on a map.
     *
     * <p>Optional because it is meaningless for straight lines: two points already describe
     * them. A provider that knows roads returns the points the road actually follows, and the
     * map can then show the drive instead of a line through the harbour.
     *
     * <p>Asked for only on the legs that end up in the answer, rather than for every pair in
     * the matrix, because a plan of n stops uses n legs out of n squared possibilities.
     */
    default List<GeoPoint> pathBetween(Location from, Location to) {
        return List.of();
    }
}
