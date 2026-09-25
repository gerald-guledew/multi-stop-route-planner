package com.solvelify.routeplanner.planning;

/**
 * Travel figures between every pair of places, indexed in the order the places were given.
 *
 * <p>A class rather than a record, for two reasons. Records compare arrays by identity, which
 * would make equality meaningless here. And this type is meant to grow: step 3 adds durations
 * and step 6 adds fuel, which is an extra method here instead of a changed signature everywhere.
 */
public final class TravelMatrix {

    private final double[][] distancesKm;

    public TravelMatrix(double[][] distancesKm) {
        this.distancesKm = copyOf(distancesKm);
    }

    public double distanceKm(int from, int to) {
        return distancesKm[from][to];
    }

    public int size() {
        return distancesKm.length;
    }

    /**
     * The table a solver minimises. Distance is the cost today. Step 6 can supply litres
     * instead without the solver knowing the difference.
     */
    public double[][] asCostMatrix() {
        return copyOf(distancesKm);
    }

    private static double[][] copyOf(double[][] source) {
        double[][] copy = new double[source.length][];
        for (int row = 0; row < source.length; row++) {
            copy[row] = source[row].clone();
        }
        return copy;
    }
}
