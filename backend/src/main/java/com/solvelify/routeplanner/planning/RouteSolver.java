package com.solvelify.routeplanner.planning;

import java.util.Set;

/**
 * Chooses the order of the stops.
 *
 * <p>A solver sees numbers, not kilometres. Whatever fills the cost matrix decides what
 * "best" means: distance or seconds today, litres later. That is why the fastest order was a
 * change to how the matrix is built, not a new algorithm, and the most fuel-efficient will be.
 */
public interface RouteSolver {

    /**
     * @param cost          square matrix where index 0 is the start and 1..n are the stops
     * @param returnToStart whether the trip finishes back at index 0
     * @param keptInPlace   the stops, by their index in the matrix, that must be visited at the
     *                      turn they were entered in: stop 1 first, stop 3 third. Only the
     *                      other stops are arranged, around these
     * @param stopOvers     the stops, by their index in the matrix, that stay between the index
     *                      before them and the index after them, so that the three are visited
     *                      one after the other and move as one. The one before stop 1 is the
     *                      start. The one after the last stop is the start again on a round
     *                      trip, and there is none on a one-way trip. The order the matrix is in
     *                      holds all of these and all of {@code keptInPlace}, so there is always
     *                      an answer
     * @return the stop indices in visiting order, excluding the start
     */
    Solution solve(double[][] cost, boolean returnToStart, Set<Integer> keptInPlace, Set<Integer> stopOvers);

    /** No stop-overs. */
    default Solution solve(double[][] cost, boolean returnToStart, Set<Integer> keptInPlace) {
        return solve(cost, returnToStart, keptInPlace, Set.of());
    }

    /** Every stop free to move. */
    default Solution solve(double[][] cost, boolean returnToStart) {
        return solve(cost, returnToStart, Set.of());
    }

    record Solution(int[] order, double totalCost, long ordersChecked) {
    }
}
