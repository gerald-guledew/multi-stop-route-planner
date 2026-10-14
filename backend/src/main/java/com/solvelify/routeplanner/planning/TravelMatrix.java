package com.solvelify.routeplanner.planning;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Travel figures between every pair of places, indexed in the order the places were given.
 *
 * <p>A class rather than a record, for two reasons. Records compare arrays by identity, which
 * would make equality meaningless here. And this type is meant to grow: it began with distances,
 * now carries durations and climbs, and step 6 adds fuel, each an extra method here instead of a
 * changed signature everywhere.
 */
public final class TravelMatrix {

    private static final double METRES_IN_A_KILOMETRE = 1000;

    private final double[][] distancesKm;

    /** Null when the source of the figures has no notion of how long a drive takes. */
    private final double[][] durationsSeconds;

    /** Null when the source of the figures does not know how high the roads are. */
    private final ClimbAndDescent[][] climbs;

    /** Distances alone, from a source that knows how far and not how long. Straight lines are one. */
    public TravelMatrix(double[][] distancesKm) {
        this.distancesKm = copyOf(distancesKm);
        this.durationsSeconds = null;
        this.climbs = null;
    }

    /** Distances and times, from roads whose heights are not known. */
    public TravelMatrix(double[][] distancesKm, double[][] durationsSeconds) {
        requireSameSize("durations", durationsSeconds.length, distancesKm.length);
        this.distancesKm = copyOf(distancesKm);
        this.durationsSeconds = copyOf(durationsSeconds);
        this.climbs = null;
    }

    /** Distances, times, and how much each drive climbs and descends. */
    public TravelMatrix(double[][] distancesKm, double[][] durationsSeconds, ClimbAndDescent[][] climbs) {
        requireSameSize("durations", durationsSeconds.length, distancesKm.length);
        requireSameSize("climbs", climbs.length, distancesKm.length);
        this.distancesKm = copyOf(distancesKm);
        this.durationsSeconds = copyOf(durationsSeconds);
        this.climbs = copyOf(climbs);
    }

    public double distanceKm(int from, int to) {
        return distancesKm[from][to];
    }

    /**
     * How long the drive takes, when the source knows. A straight line says how far apart two
     * places are and nothing about how fast anybody gets there, so for those this is empty.
     * A matrix has a duration for every pair or for none.
     */
    public OptionalDouble durationSeconds(int from, int to) {
        return durationsSeconds == null
                ? OptionalDouble.empty()
                : OptionalDouble.of(durationsSeconds[from][to]);
    }

    /**
     * How much the drive climbs and descends, when the source knows how high the roads are.
     * Empty otherwise, which is not the same as a level drive: that one is known, and is nought
     * up and nought down. A matrix has this for every pair or for none.
     */
    public Optional<ClimbAndDescent> climbAndDescent(int from, int to) {
        return climbs == null ? Optional.empty() : Optional.of(climbs[from][to]);
    }

    public int size() {
        return distancesKm.length;
    }

    /**
     * The table a solver minimises to find the shortest order: the distances. Step 6 can supply
     * litres instead without the solver knowing the difference.
     */
    public double[][] asCostMatrix() {
        return copyOf(distancesKm);
    }

    /**
     * The table for an order chosen for distance and climb together. Each drive costs its
     * distance, and on top of it {@code roadMetresPerMetreClimbed} metres of road for every
     * metre it climbs. Coming down counts for nothing.
     *
     * <p>Empty when the heights of the roads are not known. There is then no climb to count.
     */
    public Optional<double[][]> asCostMatrixCountingClimb(double roadMetresPerMetreClimbed) {
        if (climbs == null) {
            return Optional.empty();
        }
        double[][] cost = copyOf(distancesKm);
        for (int from = 0; from < cost.length; from++) {
            for (int to = 0; to < cost[from].length; to++) {
                double roadForTheClimb = climbs[from][to].climbMetres() * roadMetresPerMetreClimbed;
                cost[from][to] += roadForTheClimb / METRES_IN_A_KILOMETRE;
            }
        }
        return Optional.of(cost);
    }

    private static void requireSameSize(String what, int given, int places) {
        if (given != places) {
            throw new IllegalArgumentException(what + " for " + given + " places, distances for " + places);
        }
    }

    private static double[][] copyOf(double[][] source) {
        double[][] copy = new double[source.length][];
        for (int row = 0; row < source.length; row++) {
            copy[row] = source[row].clone();
        }
        return copy;
    }

    private static ClimbAndDescent[][] copyOf(ClimbAndDescent[][] source) {
        ClimbAndDescent[][] copy = new ClimbAndDescent[source.length][];
        for (int row = 0; row < source.length; row++) {
            copy[row] = source[row].clone();
        }
        return copy;
    }
}
