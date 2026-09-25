package com.solvelify.routeplanner.planning;

/**
 * Chooses the order of the stops.
 *
 * <p>A solver sees numbers, not kilometres. Whatever fills the cost matrix decides what
 * "best" means: distance today, minutes or litres later. That is why "fastest or most
 * fuel-efficient" will be a change to how the matrix is built, not a new algorithm.
 */
public interface RouteSolver {

    /**
     * @param cost          square matrix where index 0 is the start and 1..n are the stops
     * @param returnToStart whether the trip finishes back at index 0
     * @return the stop indices in visiting order, excluding the start
     */
    Solution solve(double[][] cost, boolean returnToStart);

    record Solution(int[] order, double totalCost, long ordersChecked) {
    }
}
