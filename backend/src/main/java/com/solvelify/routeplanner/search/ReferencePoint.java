package com.solvelify.routeplanner.search;

/**
 * Where a search is looking from, so that the nearest of several matches can come first.
 * Coordinates are WGS84 decimal degrees.
 *
 * <p>Whoever calls decides what it stands for. The map screen sends the start of the trip, or
 * the middle of the map while no start is set.
 */
public record ReferencePoint(double latitude, double longitude) {

    public ReferencePoint {
        // Written as "not inside the range" rather than "outside it", so NaN is refused too.
        if (!(latitude >= -90 && latitude <= 90)) {
            throw new IllegalArgumentException("latitude must be between -90 and 90, was " + latitude);
        }
        if (!(longitude >= -180 && longitude <= 180)) {
            throw new IllegalArgumentException("longitude must be between -180 and 180, was " + longitude);
        }
    }
}
