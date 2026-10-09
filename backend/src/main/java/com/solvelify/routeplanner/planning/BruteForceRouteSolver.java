package com.solvelify.routeplanner.planning;

import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Tries every possible order and keeps the cheapest.
 *
 * <p>Always correct, because it looks at everything. The work grows by factorial though:
 * 5 stops is 120 orders, 10 stops is 3.6 million, 12 stops is 479 million. Hence the cap.
 * A smarter, approximate solver can implement the same interface later for longer trips.
 *
 * <p>A stop kept in its place takes no part in the shuffle. Its turn is settled before the
 * search begins, so only the orders that respect it are ever built: ten stops with three
 * kept is 5,040 orders rather than 3.6 million.
 */
@Component
public class BruteForceRouteSolver implements RouteSolver {

    public static final int MAX_STOPS = 10;

    @Override
    public Solution solve(double[][] cost, boolean returnToStart, Set<Integer> keptInPlace) {
        int stopCount = cost.length - 1;
        if (stopCount < 1) {
            throw new IllegalArgumentException("at least one stop is needed");
        }
        if (stopCount > MAX_STOPS) {
            throw new IllegalArgumentException(
                    "this solver handles at most " + MAX_STOPS + " stops, was given " + stopCount);
        }

        for (int stop : keptInPlace) {
            if (stop < 1 || stop > stopCount) {
                throw new IllegalArgumentException(
                        "cannot keep stop " + stop + " in its place, there are only " + stopCount + " stops");
            }
        }

        // The search holds the state, so the bean itself stays stateless and thread safe.
        Search search = new Search(cost, returnToStart, keptInPlace);
        search.visitNext(0, 0, 0.0);
        return new Solution(search.bestOrder, search.bestCost, search.ordersChecked);
    }

    private static final class Search {

        private final double[][] cost;
        private final boolean returnToStart;
        private final int stopCount;
        private final int[] currentOrder;
        private final boolean[] visited;
        private final boolean[] kept;

        private int[] bestOrder;
        private double bestCost = Double.MAX_VALUE;
        private long ordersChecked;

        private Search(double[][] cost, boolean returnToStart, Set<Integer> keptInPlace) {
            this.cost = cost;
            this.returnToStart = returnToStart;
            this.stopCount = cost.length - 1;
            this.currentOrder = new int[stopCount];
            this.visited = new boolean[cost.length];
            this.kept = new boolean[cost.length];
            keptInPlace.forEach(stop -> kept[stop] = true);
        }

        /**
         * Classic backtracking: take an unvisited stop, go deeper, then put it back so the
         * next branch can use it.
         *
         * <p>Depth is the turn being filled, counting from 0. The stop entered at that turn
         * is {@code depth + 1}. If it is kept in its place there is nothing to choose, and
         * every other turn passes it over.
         */
        private void visitNext(int depth, int previous, double costSoFar) {
            if (depth == stopCount) {
                double total = returnToStart ? costSoFar + cost[previous][0] : costSoFar;
                ordersChecked++;
                if (total < bestCost) {
                    bestCost = total;
                    // A copy. Keeping the array itself would let later branches rewrite the winner.
                    bestOrder = currentOrder.clone();
                }
                return;
            }

            int enteredHere = depth + 1;
            if (kept[enteredHere]) {
                currentOrder[depth] = enteredHere;
                visitNext(depth + 1, enteredHere, costSoFar + cost[previous][enteredHere]);
                return;
            }

            for (int stop = 1; stop <= stopCount; stop++) {
                if (visited[stop] || kept[stop]) {
                    continue;
                }
                visited[stop] = true;
                currentOrder[depth] = stop;
                visitNext(depth + 1, stop, costSoFar + cost[previous][stop]);
                visited[stop] = false;
            }
        }
    }
}
