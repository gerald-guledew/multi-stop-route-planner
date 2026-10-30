package com.solvelify.routeplanner.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
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

    // Stop-overs. A stop-over stays between the stops either side of it in the list.

    /** Places on a line at these positions, the start first, then the stops as entered. */
    private static double[][] costAt(int... positions) {
        double[][] cost = new double[positions.length][positions.length];
        for (int from = 0; from < positions.length; from++) {
            for (int to = 0; to < positions.length; to++) {
                cost[from][to] = Math.abs(positions[from] - positions[to]);
            }
        }
        return cost;
    }

    @Test
    void drivesAStopOverStraightBetweenTheStopsEitherSideOfIt() {
        // Entered as 30, 40, 10, 20. Free, the order would be 10, 20, 30, 40.
        double[][] cost = costAt(0, 30, 40, 10, 20);

        RouteSolver.Solution solution = solver.solve(cost, false, Set.of(), Set.of(2));

        assertThat(solution.order()).containsSubsequence(1, 2, 3);
        assertThat(positionOf(2, solution.order())).isEqualTo(positionOf(1, solution.order()) + 1);
        assertThat(positionOf(3, solution.order())).isEqualTo(positionOf(2, solution.order()) + 1);
    }

    @Test
    void arrangesTheOtherStopsAroundAStopOverAndItsTwoStops() {
        // Entered as 10, 20, 30 and 5. The 20 stays between the 10 and the 30, as one block.
        double[][] cost = costAt(0, 10, 20, 30, 5);

        RouteSolver.Solution solution = solver.solve(cost, false, Set.of(), Set.of(2));

        assertThat(solution.order()).containsExactly(4, 1, 2, 3);
        assertThat(solution.totalCost()).isEqualTo(30);
    }

    @Test
    void keepsAStopOverOnTheFirstLegFirst() {
        // On the leg from the start. Free, the 5 would come first.
        double[][] cost = costAt(0, 10, 20, 5);

        RouteSolver.Solution solution = solver.solve(cost, false, Set.of(), Set.of(1));

        assertThat(solution.order()).startsWith(1, 2);
    }

    @Test
    void keepsAStopOverOnTheWayBackToTheStartLast() {
        // The last stop of a round trip is on the leg back to the start.
        double[][] cost = costAt(0, 30, 10);

        RouteSolver.Solution solution = solver.solve(cost, true, Set.of(), Set.of(2));

        assertThat(solution.order()).containsExactly(1, 2);
    }

    @Test
    void holdsSeveralStopOversInARowOnOneLeg() {
        double[][] cost = costAt(0, 10, 40, 30, 20, 5);

        RouteSolver.Solution solution = solver.solve(cost, false, Set.of(), Set.of(2, 3));

        assertThat(positionOf(2, solution.order())).isEqualTo(positionOf(1, solution.order()) + 1);
        assertThat(positionOf(3, solution.order())).isEqualTo(positionOf(2, solution.order()) + 1);
        assertThat(positionOf(4, solution.order())).isEqualTo(positionOf(3, solution.order()) + 1);
    }

    @Test
    void holdsAStopOverOnlyToTheStopBeforeItWhenNoneFollowsOnAOneWayTrip() {
        // Nothing follows the last stop of a one-way trip, so it stays after the stop before it.
        double[][] cost = costAt(0, 30, 10, 20);

        RouteSolver.Solution solution = solver.solve(cost, false, Set.of(), Set.of(3));

        assertThat(positionOf(3, solution.order())).isEqualTo(positionOf(2, solution.order()) + 1);
    }

    @Test
    void holdsAStopOverAndAKeptStopTogether() {
        // The 40 is kept second, and the block of 10, 20 and 30 goes round it the one way it can.
        double[][] cost = costAt(0, 5, 40, 10, 20, 30);

        RouteSolver.Solution solution = solver.solve(cost, false, Set.of(2), Set.of(4));

        assertThat(solution.order()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void checksFewerOrdersWithAStopOver() {
        double[][] cost = costAt(0, 10, 20, 30, 40);

        assertThat(solver.solve(cost, false).ordersChecked()).isEqualTo(24);
        // The block of three and the one stop left: two orders.
        assertThat(solver.solve(cost, false, Set.of(), Set.of(2)).ordersChecked()).isEqualTo(2);
    }

    @Test
    void findsTheBestOrderThatHoldsEverythingAsLookingAtEveryOrderDoes() {
        Random random = new Random(7);
        for (int trip = 0; trip < 3000; trip++) {
            int stopCount = 1 + random.nextInt(7);
            boolean returnToStart = random.nextBoolean();
            double[][] cost = randomCost(random, stopCount);
            Set<Integer> kept = new TreeSet<>();
            Set<Integer> stopOvers = new TreeSet<>();
            for (int stop = 1; stop <= stopCount; stop++) {
                double roll = random.nextDouble();
                if (roll < 0.2) {
                    kept.add(stop);
                } else if (roll < 0.4) {
                    stopOvers.add(stop);
                }
            }

            RouteSolver.Solution solution = solver.solve(cost, returnToStart, kept, stopOvers);

            // The order sent always holds everything, so there is always an answer.
            assertThat(solution.order()).as("trip %d", trip).isNotNull();
            assertThat(solution.totalCost()).as("trip %d", trip)
                    .isCloseTo(bestByLookingAtEveryOrder(cost, returnToStart, kept, stopOvers), within(1e-9));
        }
    }

    private static double[][] randomCost(Random random, int stopCount) {
        double[][] cost = new double[stopCount + 1][stopCount + 1];
        for (double[] row : cost) {
            Arrays.setAll(row, column -> random.nextDouble() * 100);
        }
        return cost;
    }

    private static double bestByLookingAtEveryOrder(
            double[][] cost, boolean returnToStart, Set<Integer> kept, Set<Integer> stopOvers) {
        List<Integer> stops = new ArrayList<>();
        for (int stop = 1; stop < cost.length; stop++) {
            stops.add(stop);
        }
        return everyOrderOf(stops).stream()
                .filter(order -> kept.stream().allMatch(stop -> order.get(stop - 1) == stop))
                .filter(order -> stopOvers.stream().allMatch(stop -> holds(order, stop, returnToStart)))
                .mapToDouble(order -> costOf(order, cost, returnToStart))
                .min()
                .orElseThrow();
    }

    private static List<List<Integer>> everyOrderOf(List<Integer> stops) {
        if (stops.isEmpty()) {
            return List.of(List.of());
        }
        List<List<Integer>> orders = new ArrayList<>();
        for (Integer first : stops) {
            List<Integer> others = new ArrayList<>(stops);
            others.remove(first);
            for (List<Integer> rest : everyOrderOf(others)) {
                List<Integer> order = new ArrayList<>(List.of(first));
                order.addAll(rest);
                orders.add(order);
            }
        }
        return orders;
    }

    /** Whether a stop-over has the same places before and after it as it had in the list. */
    private static boolean holds(List<Integer> order, int stop, boolean returnToStart) {
        int turn = order.indexOf(stop);
        int before = turn == 0 ? 0 : order.get(turn - 1);
        if (before != stop - 1) {
            return false;
        }
        if (stop == order.size()) {
            return !returnToStart || turn == order.size() - 1;
        }
        return turn < order.size() - 1 && order.get(turn + 1) == stop + 1;
    }

    private static double costOf(List<Integer> order, double[][] cost, boolean returnToStart) {
        double total = 0;
        int previous = 0;
        for (int stop : order) {
            total += cost[previous][stop];
            previous = stop;
        }
        return returnToStart ? total + cost[previous][0] : total;
    }

    private static int positionOf(int stop, int[] order) {
        for (int turn = 0; turn < order.length; turn++) {
            if (order[turn] == stop) {
                return turn;
            }
        }
        throw new IllegalArgumentException("stop " + stop + " is not in the order");
    }

    @Test
    void refusesAStopOverThatIsNotThere() {
        assertThatThrownBy(() -> solver.solve(costOnALine(), false, Set.of(), Set.of(9)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("9");
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
