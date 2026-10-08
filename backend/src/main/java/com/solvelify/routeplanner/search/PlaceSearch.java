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

    List<FoundPlace> search(String typed, int limit);
}
