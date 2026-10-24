package com.solvelify.routeplanner.planning;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Travel figures between every pair of places, indexed in the order the places were given.
 *
 * <p>A class rather than a record, for two reasons. Records compare arrays by identity, which
 * would make equality meaningless here. And this type is meant to grow: it began with distances,
 * and now carries durations, climbs and how much of each drive is in town, each an extra method
 * here instead of a changed signature everywhere.
 */
public final class TravelMatrix {

    private final double[][] distancesKm;

    /** Null when the source of the figures has no notion of how long a drive takes. */
    private final double[][] durationsSeconds;

    /** Null when the source of the figures does not know how high the roads are. */
    private final ClimbAndDescent[][] climbs;

    /** Null when the source of the figures does not know how fast each road is driven. */
    private final double[][] kmInTown;

    /** Distances alone, from a source that knows how far and not how long. Straight lines are one. */
    public TravelMatrix(double[][] distancesKm) {
        this.distancesKm = copyOf(distancesKm);
        this.durationsSeconds = null;
        this.climbs = null;
        this.kmInTown = null;
    }

    /** Distances and times, from roads whose heights are not known. */
    public TravelMatrix(double[][] distancesKm, double[][] durationsSeconds) {
        requireSameSize("durations", durationsSeconds.length, distancesKm.length);
        this.distancesKm = copyOf(distancesKm);
        this.durationsSeconds = copyOf(durationsSeconds);
        this.climbs = null;
        this.kmInTown = null;
    }

    /** Distances, times, and how much each drive climbs and descends. */
    public TravelMatrix(double[][] distancesKm, double[][] durationsSeconds, ClimbAndDescent[][] climbs) {
        requireSameSize("durations", durationsSeconds.length, distancesKm.length);
        requireSameSize("climbs", climbs.length, distancesKm.length);
        this.distancesKm = copyOf(distancesKm);
        this.durationsSeconds = copyOf(durationsSeconds);
        this.climbs = copyOf(climbs);
        this.kmInTown = null;
    }

    /** The tables of another matrix, which are copies already and never change, and one more. */
    private TravelMatrix(
            double[][] distancesKm, double[][] durationsSeconds, ClimbAndDescent[][] climbs, double[][] kmInTown) {
        this.distancesKm = distancesKm;
        this.durationsSeconds = durationsSeconds;
        this.climbs = climbs;
        this.kmInTown = kmInTown;
    }

    /**
     * The same figures, with how many kilometres of each drive are on town roads. Roads tell
     * that from how fast each stretch of them is driven. Straight lines cannot.
     */
    public TravelMatrix withKmInTown(double[][] kmInTown) {
        requireSameSize("km in town", kmInTown.length, distancesKm.length);
        return new TravelMatrix(distancesKm, durationsSeconds, climbs, copyOf(kmInTown));
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

    /**
     * How many kilometres of the drive are on town roads, when the source knows how fast each
     * road is driven. The rest of the drive is open road. Empty on straight lines. A matrix has
     * this for every pair or for none.
     */
    public OptionalDouble kmInTown(int from, int to) {
        return kmInTown == null ? OptionalDouble.empty() : OptionalDouble.of(kmInTown[from][to]);
    }

    public int size() {
        return distancesKm.length;
    }

    /**
     * The table a solver minimises to find the shortest order: the distances. The tables below
     * hold seconds or litres instead, without the solver knowing the difference.
     */
    public double[][] asCostMatrix() {
        return copyOf(distancesKm);
    }

    /**
     * The table for an order chosen for driving time: how long each drive takes.
     *
     * <p>Empty when the source has no times. A straight line says how far and not how long.
     */
    public Optional<double[][]> asCostMatrixOfDrivingTime() {
        return durationsSeconds == null ? Optional.empty() : Optional.of(copyOf(durationsSeconds));
    }

    /**
     * The table for an order chosen for the least fuel: the litres each drive uses up for good in
     * one vehicle. On the level, its town figure for the kilometres in town and its open road's
     * for the rest. On descents too steep to give back what climbing them cost, what they brake
     * away. Over a round trip they add up to the fuel it takes. A one-way trip is not made to end
     * at its lowest stop, which would only leave the car lower: see {@link FuelUse#litresUsedUpFor}.
     *
     * <p>Empty when neither how fast the roads are driven nor their hills can be counted, as on
     * straight lines. The fuel then goes with the distance, and the shortest order already takes
     * the least.
     */
    public Optional<double[][]> asCostMatrixOfFuel(FuelUse vehicle) {
        boolean countsHills = climbs != null && vehicle.countsHills();
        if (kmInTown == null && !countsHills) {
            return Optional.empty();
        }
        double[][] cost = new double[distancesKm.length][distancesKm.length];
        for (int from = 0; from < cost.length; from++) {
            for (int to = 0; to < cost[from].length; to++) {
                cost[from][to] = vehicle.litresUsedUpFor(
                        distancesKm[from][to], kmInTown(from, to), climbAndDescent(from, to));
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
