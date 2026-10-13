package com.solvelify.routeplanner.distance;

import com.solvelify.routeplanner.planning.ClimbAndDescent;

/**
 * Works out how much a drive climbs and descends from the heights along it.
 *
 * <p>Adding up every rise and every fall between neighbouring points, which is what the routing
 * engine does, gives figures nobody could use. The heights come from a grid of the ground, a
 * whole number of metres every 30 m or so, and the ground is not the road. A road cut into a
 * hillside is read partly off the slope above it and partly off the slope below. Measured on the
 * real map, a waterfront road that never leaves sea level came out as 176 m of climbing in 8 km,
 * and a road across a plain as 113 m in 19 km.
 *
 * <p>So two things are done to the heights first. Both were settled by measuring, on passes
 * whose height is published and on roads known to be level.
 *
 * <ol>
 *   <li>Each height is evened out with the road around it, over {@link #EVENED_OVER_METRES}.
 *       One wrong height then moves the road very little.
 *   <li>A rise or a dip is counted only once the road has turned by {@link #LEAST_TURN_METRES}.
 *       Anything smaller is taken for a wobble in the grid. What is counted is counted in full,
 *       from one turn to the next.
 * </ol>
 *
 * <p>The two ends of a drive keep the heights they came with. So the climb less the descent is
 * exactly how much higher the drive ends than it began, and a trip that comes back to where it
 * started climbs as much as it descends.
 */
final class HeightProfile {

    /**
     * How much road a height is evened out over, half of it either side. Shorter leaves the
     * wrong heights of a steep hillside standing. Longer starts to level real hills: a hill
     * 600 m from foot to foot keeps seven eighths of its height at this setting.
     */
    static final double EVENED_OVER_METRES = 150;

    /**
     * The least a road has to turn, up or down, for it to count. At 5 m the level waterfront
     * road still climbed 57 m. At 10 m it climbs 37 m, and five passes that are one steady climb
     * come to within 6% of the height they gain. A real rise smaller than this is lost with the
     * wobbles, which is the price.
     */
    static final double LEAST_TURN_METRES = 10;

    private HeightProfile() {
    }

    /**
     * @param metresAlong how far along the drive each point is, measured on the map, starting at 0
     * @param heights     the height of each point, as read off the grid
     */
    static ClimbAndDescent climbAndDescent(double[] metresAlong, double[] heights) {
        if (metresAlong.length != heights.length) {
            throw new IllegalArgumentException(
                    metresAlong.length + " distances along the drive, " + heights.length + " heights");
        }
        return counted(evenedOut(metresAlong, heights, EVENED_OVER_METRES), LEAST_TURN_METRES);
    }

    /**
     * Each height replaced by the average height of the road for half the stretch either side
     * of it. The road between two points is taken as an even slope, and the average is over
     * road, not over points, so a stretch described by many points weighs no more than one
     * described by few.
     *
     * <p>Near an end there is less road on one side than half the stretch, and then only that
     * much is taken on both sides. An even slope therefore comes out exactly as it went in, and
     * the two ends themselves are not moved at all.
     */
    static double[] evenedOut(double[] metresAlong, double[] heights, double overMetres) {
        int last = heights.length - 1;
        double[] evened = new double[heights.length];
        for (int at = 0; at <= last; at++) {
            double reach = Math.min(overMetres / 2,
                    Math.min(metresAlong[at] - metresAlong[0], metresAlong[last] - metresAlong[at]));
            evened[at] = reach > 0
                    ? averageHeight(metresAlong, heights, at, metresAlong[at] - reach, metresAlong[at] + reach)
                    : heights[at];
        }
        return evened;
    }

    /** The average height of the road between two distances along it, which lie either side of a point. */
    private static double averageHeight(double[] metresAlong, double[] heights, int point, double from, double to) {
        double area = 0;
        for (int end = point; end > 0 && metresAlong[end] > from; end--) {
            area += areaUnder(metresAlong, heights, end, from, to);
        }
        for (int end = point + 1; end < heights.length && metresAlong[end - 1] < to; end++) {
            area += areaUnder(metresAlong, heights, end, from, to);
        }
        return area / (to - from);
    }

    /** The area under the stretch that ends at a point, as far as it lies between the two distances. */
    private static double areaUnder(double[] metresAlong, double[] heights, int end, double from, double to) {
        double startsAt = Math.max(from, metresAlong[end - 1]);
        double endsAt = Math.min(to, metresAlong[end]);
        // Nothing of this stretch is in reach. Also true of a point given twice, which is a
        // stretch with no length to it.
        if (endsAt <= startsAt) {
            return 0;
        }
        double slope = (heights[end] - heights[end - 1]) / (metresAlong[end] - metresAlong[end - 1]);
        double heightAtStart = heights[end - 1] + slope * (startsAt - metresAlong[end - 1]);
        double heightAtEnd = heights[end - 1] + slope * (endsAt - metresAlong[end - 1]);
        return (heightAtStart + heightAtEnd) / 2 * (endsAt - startsAt);
    }

    /**
     * Adds up the rises and the falls, passing over any turn smaller than the least.
     *
     * <p>The road is followed from its last turn. While it keeps going the same way, the furthest
     * point it reaches is remembered. Only when it has come back from that point by the least
     * amount is the stretch from the last turn to the furthest point counted, and the furthest
     * point becomes the new turn. A dip of a few metres on the way up a hill never comes back
     * far enough, so it is not a turn, and the hill is counted as one climb.
     *
     * <p>What is left at the end is the height between the last turn and the end of the drive,
     * whichever way that goes. So the climb less the descent is always the height between the two
     * ends, and a drive taken the other way gives the same two figures with their places changed.
     */
    static ClimbAndDescent counted(double[] heights, double leastTurnMetres) {
        if (heights.length < 2) {
            return ClimbAndDescent.LEVEL;
        }
        double climb = 0;
        double descent = 0;
        double lastTurn = heights[0];
        double furthest = heights[0];
        // Which way the road is heading is not known until it has moved by the least amount,
        // measured between the lowest and the highest it has been so far.
        double lowest = heights[0];
        double highest = heights[0];
        Heading heading = Heading.NOT_YET_KNOWN;

        for (int at = 1; at < heights.length; at++) {
            double here = heights[at];
            switch (heading) {
                case NOT_YET_KNOWN -> {
                    lowest = Math.min(lowest, here);
                    highest = Math.max(highest, here);
                    if (highest - lowest >= leastTurnMetres) {
                        heading = here == highest ? Heading.UP : Heading.DOWN;
                        furthest = here;
                    }
                }
                case UP -> {
                    if (here > furthest) {
                        furthest = here;
                    } else if (furthest - here >= leastTurnMetres) {
                        climb += furthest - lastTurn;
                        lastTurn = furthest;
                        furthest = here;
                        heading = Heading.DOWN;
                    }
                }
                case DOWN -> {
                    if (here < furthest) {
                        furthest = here;
                    } else if (here - furthest >= leastTurnMetres) {
                        descent += lastTurn - furthest;
                        lastTurn = furthest;
                        furthest = here;
                        heading = Heading.UP;
                    }
                }
            }
        }

        double leftOver = heights[heights.length - 1] - lastTurn;
        if (leftOver > 0) {
            climb += leftOver;
        } else {
            descent -= leftOver;
        }
        return new ClimbAndDescent(climb, descent);
    }

    private enum Heading { NOT_YET_KNOWN, UP, DOWN }
}
