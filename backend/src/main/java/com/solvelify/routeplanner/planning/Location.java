package com.solvelify.routeplanner.planning;

/**
 * A place on the map. Coordinates are WGS84 decimal degrees, the numbers Google Maps shows.
 *
 * <p>The compact constructor refuses impossible coordinates, so a Location is always valid
 * no matter who builds one. The API layer validates the same ranges separately, because a
 * caller sending bad JSON deserves a 400 with the field name rather than a 500.
 */
public record Location(String name, double latitude, double longitude) {

    public Location {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude must be between -90 and 90, was " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude must be between -180 and 180, was " + longitude);
        }
    }
}
