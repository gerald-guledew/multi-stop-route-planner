package com.solvelify.routeplanner.planning;

import java.util.List;

/**
 * How to drive from one place to the next: the road's shape, for drawing it on the map, and its
 * turns written out, for following it.
 *
 * @param path  the points the road follows. Empty on straight lines
 * @param turns the turns along it, from setting off to arriving. Empty on straight lines
 */
public record Directions(List<GeoPoint> path, List<Turn> turns) {

    /** What a straight line gives: nothing to draw beyond its two ends, and no turns. */
    public static final Directions NONE = new Directions(List.of(), List.of());

    public Directions {
        path = List.copyOf(path);
        turns = List.copyOf(turns);
    }
}
