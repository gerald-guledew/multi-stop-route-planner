package com.solvelify.routeplanner.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BruteForceRouteSolverTest {

    private final BruteForceRouteSolver solver = new BruteForceRouteSolver();

    /**
     * Four points on a straight line, so the right answer is obvious by hand. The start sits at
     * 0, and the stops are deliberately entered in a silly order: 30, 10, 20.
     */
    private static final double[] POSITIONS = {0, 30, 10, 20};

    private static double[][] costOnALine() {
        double[][] cost = new double[POSITIONS.length][POSITIONS.length];
        for (int from = 0; from < POSITIONS.length; from++) {
            for (int to = 0; to < POSITIONS.length; to++) {
                cost[from][to] = Math.abs(POSITIONS[from] - POSITIONS[to]);
            }
        }
        return cost;
    }

    @Test
    void walksTheLineInOrderInsteadOfZigZagging() {
        RouteSolver.Solution solution = solver.solve(costOnALine(), false);

        assertThat(solution.order()).containsExactly(2, 3, 1);
        assertThat(solution.totalCost()).isEqualTo(30);
    }

    @Test
    void beatsTheOrderTheStopsArrivedIn() {
        // As entered: 0 to 30 to 10 to 20 is 30 + 20 + 10 = 60, double the best answer.
        RouteSolver.Solution solution = solver.solve(costOnALine(), false);

        assertThat(solution.totalCost()).isLessThan(60);
    }

    @Test
    void addsTheDriveHomeOnARoundTrip() {
        RouteSolver.Solution solution = solver.solve(costOnALine(), true);

        assertThat(solution.totalCost()).isEqualTo(60);
    }

    @Test
    void checksEveryPossibleOrder() {
        RouteSolver.Solution solution = solver.solve(costOnALine(), false);

        // Three stops, so 3 x 2 x 1 orders.
        assertThat(solution.ordersChecked()).isEqualTo(6);
    }

    @Test
    void handlesASingleStop() {
        double[][] cost = {{0, 5}, {5, 0}};

        RouteSolver.Solution solution = solver.solve(cost, true);

        assertThat(solution.order()).containsExactly(1);
        assertThat(solution.totalCost()).isEqualTo(10);
        assertThat(solution.ordersChecked()).isEqualTo(1);
    }

    @Test
    void refusesMoreStopsThanItCanCheckInReasonableTime() {
        double[][] tooMany = new double[BruteForceRouteSolver.MAX_STOPS + 2][BruteForceRouteSolver.MAX_STOPS + 2];

        assertThatThrownBy(() -> solver.solve(tooMany, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 10 stops");
    }

    @Test
    void refusesARequestWithNoStops() {
        assertThatThrownBy(() -> solver.solve(new double[][] {{0}}, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
