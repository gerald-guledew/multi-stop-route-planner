package com.solvelify.routeplanner.planning;

import java.util.Arrays;

/**
 * The drive between each pair of places, and which roads it follows.
 *
 * <p>When climb is counted, a drive follows the roads that climb less where they climb at least
 * {@value #LEAST_CLIMB_SAVED_METRES} m less than the usual roads and come to less, by the
 * planner's own count: the distance, and so many metres of road for every metre climbed.
 * Otherwise it follows the usual roads. The engine finds the roads that climb less counting every
 * rise of the ground along them, wobbles of the height grid included, where the planner evens the
 * heights out. So by the planner's count those roads can come out longer and climbing more.
 */
final class Drives {

    /**
     * Less climb saved than this is no reason to change roads. The heights are good to about
     * 10 m, and the climb of a drive counts only rises of 10 m or more.
     */
    static final double LEAST_CLIMB_SAVED_METRES = 10;

    private final TravelMatrix matrix;
    private final Roads[][] roads;

    private Drives(TravelMatrix matrix, Roads[][] roads) {
        this.matrix = matrix;
        this.roads = roads;
    }

    /** Every drive along the usual roads. */
    static Drives alongTheUsualRoads(TravelMatrix usual) {
        Roads[][] roads = new Roads[usual.size()][usual.size()];
        for (Roads[] row : roads) {
            Arrays.fill(row, Roads.USUAL);
        }
        return new Drives(usual, roads);
    }

    /**
     * Each drive along the roads that climb less where they are worth it, and along the usual
     * roads otherwise. Without heights there is no climb to count, and the usual roads are kept.
     */
    static Drives climbingLessWhereWorthIt(
            TravelMatrix usual, TravelMatrix climbingLess, double roadMetresPerMetreClimbed) {
        if (!usual.knowsClimb() || !climbingLess.knowsClimb()) {
            return alongTheUsualRoads(usual);
        }
        double[][] usualCost = usual.asCostMatrixCountingClimb(roadMetresPerMetreClimbed).orElseThrow();
        double[][] climbingLessCost = climbingLess.asCostMatrixCountingClimb(roadMetresPerMetreClimbed).orElseThrow();

        int size = usual.size();
        double[][] distances = new double[size][size];
        double[][] durations = new double[size][size];
        ClimbAndDescent[][] climbs = new ClimbAndDescent[size][size];
        Roads[][] roads = new Roads[size][size];
        for (int from = 0; from < size; from++) {
            for (int to = 0; to < size; to++) {
                double climbSaved = usual.climbAndDescent(from, to).orElseThrow().climbMetres()
                        - climbingLess.climbAndDescent(from, to).orElseThrow().climbMetres();
                boolean worthIt = climbSaved >= LEAST_CLIMB_SAVED_METRES
                        && climbingLessCost[from][to] < usualCost[from][to];
                TravelMatrix taken = worthIt ? climbingLess : usual;
                roads[from][to] = worthIt ? Roads.CLIMBING_LESS : Roads.USUAL;
                distances[from][to] = taken.distanceKm(from, to);
                durations[from][to] = taken.durationSeconds(from, to).orElseThrow();
                climbs[from][to] = taken.climbAndDescent(from, to).orElseThrow();
            }
        }
        return new Drives(new TravelMatrix(distances, durations, climbs), roads);
    }

    TravelMatrix matrix() {
        return matrix;
    }

    /** Which roads the drive from one place to another follows. */
    Roads roads(int from, int to) {
        return roads[from][to];
    }
}
