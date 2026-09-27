package com.solvelify.routeplanner.address;

import com.solvelify.routeplanner.planning.Location;
import java.util.List;

/**
 * Turns typed text into places with coordinates.
 *
 * <p>An interface for the same reason distances have one: the New Zealand implementation reads
 * LINZ data from PostgreSQL, and somebody routing in another country can supply their own
 * without the endpoint or the map screen changing.
 */
public interface AddressSearch {

    List<Location> search(String term, int limit);
}
