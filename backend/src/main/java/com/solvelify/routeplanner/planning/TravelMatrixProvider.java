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
}
