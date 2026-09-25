package com.solvelify.routeplanner.distance;

import com.solvelify.routeplanner.planning.Location;

/**
 * Great-circle distance between two points, the distance a bird flies.
 *
 * <p>This lives apart from {@link Location} on purpose. A Location is a place, and how far
 * apart two places are depends on who is asking: straight line today, driving distance from
 * step 3 onwards. Putting the method on the record would tie the domain to one answer.
 */
public final class Haversine {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private Haversine() {
    }

    public static double distanceKm(Location from, Location to) {
        double latitudeDelta = Math.toRadians(to.latitude() - from.latitude());
        double longitudeDelta = Math.toRadians(to.longitude() - from.longitude());
        double fromLatitude = Math.toRadians(from.latitude());
        double toLatitude = Math.toRadians(to.latitude());

        double a = square(Math.sin(latitudeDelta / 2))
                + Math.cos(fromLatitude) * Math.cos(toLatitude) * square(Math.sin(longitudeDelta / 2));

        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }

    private static double square(double value) {
        return value * value;
    }
}
