package com.solvelify.routeplanner.search;

import java.util.List;

/**
 * Turns typed text into places with coordinates, whether it was an address or a name.
 *
 * <p>An interface for the same reason distances have one: the New Zealand implementation
 * reads LINZ addresses and Overture places from PostgreSQL, and somebody routing in another
 * country can supply their own without the endpoint or the map screen changing.
 */
public interface PlaceSearch {

    /**
     * @param near where the search is looking from, so that the nearest of several matches
     *             comes first. Null when that is not known, and places are then ranked on the
     *             words alone
     */
    List<FoundPlace> search(String typed, ReferencePoint near, int limit);

    /** Ranks on the words alone, for a caller with no idea where the person is. */
    default List<FoundPlace> search(String typed, int limit) {
        return search(typed, null, limit);
    }
}
