package com.solvelify.routeplanner.distance;

import com.graphhopper.routing.weighting.AbstractAdjustedWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;

/**
 * Weighs a stretch of road as the engine does, and then as if it were longer by so many metres
 * for every metre it climbs. The same rule the order is chosen by when road climbs are factored
 * in, so the roads between two stops can go round a hill as the order can.
 *
 * <p>Only the weight changes. How long the stretch takes to drive is the engine's own figure.
 *
 * <p>The climb is read off the heights of the stretch's own points. The engine also keeps a slope
 * for each stretch, but only from one end to the other, so a hill between two junctions would
 * read as level. Every rise counts, however small: a stretch between two junctions is often too
 * short for anything to be evened out, and the wobble of the grid then weighs on every road
 * alike.
 */
final class ClimbCountedAsRoad extends AbstractAdjustedWeighting {

    /**
     * Raise this whenever the weight given to a stretch changes. The engine prepares the roads
     * that climb less ahead and keeps them on disk, and this number is part of their profile, so
     * a graph prepared with an older weighting is refused rather than read.
     */
    static final int VERSION = 1;

    private final double roadMetresPerMetreClimbed;

    ClimbCountedAsRoad(Weighting usual, double roadMetresPerMetreClimbed) {
        super(usual);
        this.roadMetresPerMetreClimbed = roadMetresPerMetreClimbed;
    }

    /**
     * @param reverse whether the stretch is driven from its far end, the way the engine's own
     *                weighting reads it
     */
    @Override
    public double calcEdgeWeight(EdgeIteratorState stretch, boolean reverse) {
        double weight = superWeighting.calcEdgeWeight(stretch, reverse);
        double metres = stretch.getDistance();
        if (Double.isInfinite(weight) || metres <= 0) {
            return weight;
        }
        double climbed = metresClimbed(stretch.fetchWayGeometry(FetchMode.ALL), reverse);
        return weight * (1 + roadMetresPerMetreClimbed * climbed / metres);
    }

    /**
     * How far a line of points climbs, every rise along it added up.
     *
     * @param backwards whether it is driven from its last point to its first, so that what goes
     *                  down the line is what climbs
     */
    static double metresClimbed(PointList points, boolean backwards) {
        double climbed = 0;
        for (int at = 1; at < points.size(); at++) {
            double rise = points.getEle(at) - points.getEle(at - 1);
            if (backwards) {
                rise = -rise;
            }
            // Points read without heights have none, and NaN is no rise.
            if (rise > 0) {
                climbed += rise;
            }
        }
        return climbed;
    }

    @Override
    public String getName() {
        return "climb_counted_as_road";
    }
}
