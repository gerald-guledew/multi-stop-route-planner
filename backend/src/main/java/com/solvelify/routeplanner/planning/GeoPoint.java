package com.solvelify.routeplanner.planning;

/**
 * A point on a drawn line. Unlike {@link Location} it has no name, because the hundreds of
 * points that trace a road are shape, not places anyone asked to visit.
 */
public record GeoPoint(double latitude, double longitude) {
}
