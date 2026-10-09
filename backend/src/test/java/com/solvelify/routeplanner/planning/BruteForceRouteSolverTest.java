package com.solvelify.routeplanner.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
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

    // Stops kept in their place. On the line, the stops were entered as 30, 10, 20.

    @Test
    void visitsAKeptStopAtTheTurnItWasEnteredIn() {
        // The far end first, because it has to be. Then back along the line: 30 + 10 + 10.
        RouteSolver.Solution solution = solver.solve(costOnALine(), false, Set.of(1));

        assertThat(solution.order()).containsExactly(1, 3, 2);
        assertThat(solution.totalCost()).isEqualTo(50);
    }

    @Test
    void arrangesTheOtherStopsAroundAKeptOne() {
        // The stop at 20 was entered third and stays third: 10 + 20 + 10.
        RouteSolver.Solution solution = solver.solve(costOnALine(), false, Set.of(3));

        assertThat(solution.order()).containsExactly(2, 1, 3);
        assertThat(solution.totalCost()).isEqualTo(40);
    }

    @Test
    void checksOnlyTheOrdersThatLeaveKeptStopsAlone() {
        // One stop kept leaves two to arrange, so 2 x 1 orders, not 3 x 2 x 1.
        assertThat(solver.solve(costOnALine(), false, Set.of(1)).ordersChecked()).isEqualTo(2);
        assertThat(solver.solve(costOnALine(), false, Set.of(1, 3)).ordersChecked()).isEqualTo(1);
    }

    @Test
    void keepsTheOrderEnteredWhenEveryStopIsKept() {
        RouteSolver.Solution solution = solver.solve(costOnALine(), false, Set.of(1, 2, 3));

        assertThat(solution.order()).containsExactly(1, 2, 3);
        assertThat(solution.totalCost()).isEqualTo(60);
        assertThat(solution.ordersChecked()).isEqualTo(1);
    }

    @Test
    void stillAddsTheDriveHomeWhenAStopIsKept() {
        // 30 + 10 + 10, then 10 home.
        RouteSolver.Solution solution = solver.solve(costOnALine(), true, Set.of(1));

        assertThat(solution.order()).containsExactly(1, 3, 2);
        assertThat(solution.totalCost()).isEqualTo(60);
    }

    @Test
    void neverBeatsTheAnswerWithNothingKept() {
        // Keeping a stop can only take choices away.
        double free = solver.solve(costOnALine(), false).totalCost();

        assertThat(solver.solve(costOnALine(), false, Set.of(1)).totalCost()).isGreaterThanOrEqualTo(free);
        assertThat(solver.solve(costOnALine(), false, Set.of(2)).totalCost()).isGreaterThanOrEqualTo(free);
        assertThat(solver.solve(costOnALine(), false, Set.of(3)).totalCost()).isGreaterThanOrEqualTo(free);
    }

    @Test
    void refusesToKeepAStopThatIsNotThere() {
        assertThatThrownBy(() -> solver.solve(costOnALine(), false, Set.of(4)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only 3 stops");

        // Index 0 is the start, which is not a stop.
        assertThatThrownBy(() -> solver.solve(costOnALine(), false, Set.of(0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
