package com.solvelify.routeplanner.along;

import com.solvelify.routeplanner.planning.GeoPoint;
import java.util.List;

/**
 * Distances short enough for the ground to be taken as flat: from a place to a road beside it,
 * or between two points of one road. Out by a fraction of a percent over a few kilometres, which
 * is nothing to telling which road passes nearest.
 */
final class Flat {

    /** A degree of latitude, anywhere, on a round earth of the usual radius. */
    private static final double METRES_PER_DEGREE = 6_371_000 * Math.PI / 180;

    private Flat() {
    }

    static double metresBetween(GeoPoint from, GeoPoint to) {
        double east = eastOf(from, to);
        double north = northOf(from, to);
        return Math.hypot(east, north);
    }

    /** How near a line of points passes to a point. A line of one point is that point. */
    static double metresFromLine(GeoPoint point, List<GeoPoint> line) {
        if (line.size() == 1) {
            return metresBetween(point, line.getFirst());
        }
        double nearest = Double.MAX_VALUE;
        for (int at = 0; at + 1 < line.size(); at++) {
            nearest = Math.min(nearest, metresFromStretch(point, line.get(at), line.get(at + 1)));
        }
        return nearest;
    }

    /** Measured with the point as the origin, east and north of it in metres. */
    private static double metresFromStretch(GeoPoint point, GeoPoint start, GeoPoint end) {
        double startEast = eastOf(point, start);
        double startNorth = northOf(point, start);
        double alongEast = eastOf(point, end) - startEast;
        double alongNorth = northOf(point, end) - startNorth;
        double lengthSquared = alongEast * alongEast + alongNorth * alongNorth;
        // How far along the stretch the nearest spot is, from 0 at its start to 1 at its end.
        double share = lengthSquared == 0
                ? 0
                : Math.clamp(-(startEast * alongEast + startNorth * alongNorth) / lengthSquared, 0, 1);
        return Math.hypot(startEast + share * alongEast, startNorth + share * alongNorth);
    }

    private static double eastOf(GeoPoint origin, GeoPoint point) {
        return (point.longitude() - origin.longitude())
                * Math.cos(Math.toRadians(origin.latitude())) * METRES_PER_DEGREE;
    }

    private static double northOf(GeoPoint origin, GeoPoint point) {
        return (point.latitude() - origin.latitude()) * METRES_PER_DEGREE;
    }
}
