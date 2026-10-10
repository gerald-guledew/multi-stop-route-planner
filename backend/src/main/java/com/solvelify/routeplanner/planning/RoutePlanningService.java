package com.solvelify.routeplanner.planning;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Turns a start and a list of stops into a plan.
 *
 * <p>It depends on two interfaces and nothing else. Swapping straight lines for real roads,
 * or brute force for something cleverer, happens in the beans passed in here.
 */
@Service
public class RoutePlanningService {

    private final TravelMatrixProvider travelMatrixProvider;
    private final RouteSolver routeSolver;

    public RoutePlanningService(TravelMatrixProvider travelMatrixProvider, RouteSolver routeSolver) {
        this.travelMatrixProvider = travelMatrixProvider;
        this.routeSolver = routeSolver;
    }

    /** Every stop free to move. */
    public RoutePlan plan(Location start, List<Location> stops, boolean returnToStart) {
        return plan(start, stops, Set.of(), returnToStart);
    }

    /**
     * @param keptInPlace positions in {@code stops}, counting from 0, of the stops that must be
     *                    visited at the turn they were entered in. The first stop of the day
     *                    that cannot wait, say. The others are arranged around them
     */
    public RoutePlan plan(
            Location start, List<Location> stops, Set<Integer> keptInPlace, boolean returnToStart) {

        for (int position : keptInPlace) {
            if (position < 0 || position >= stops.size()) {
                throw new IllegalArgumentException(
                        "cannot keep position " + position + " in its place, there are only "
                                + stops.size() + " stops");
            }
        }

        List<Location> places = new ArrayList<>(stops.size() + 1);
        places.add(start);
        places.addAll(stops);

        TravelMatrix matrix = travelMatrixProvider.matrixFor(places);

        // Cost is distance today. Step 6 fills this table with litres instead.
        RouteSolver.Solution solution =
                routeSolver.solve(matrix.asCostMatrix(), returnToStart, asMatrixIndices(keptInPlace));

        int[] bestPath = pathOf(solution.order(), returnToStart);
        int[] enteredPath = pathOf(stopsInEnteredOrder(stops.size()), returnToStart);

        return new RoutePlan(
                placesAlong(bestPath, places),
                legsAlong(bestPath, places, matrix),
                solution.totalCost(),
                distanceAlong(enteredPath, matrix),
                solution.ordersChecked());
    }

    /** The matrix puts the start at 0, so the stop at position 0 of the list is index 1. */
    private static Set<Integer> asMatrixIndices(Set<Integer> positionsAmongStops) {
        return positionsAmongStops.stream()
                .map(position -> position + 1)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Wraps a stop order into a full path: start, the stops, and home again when asked. */
    private static int[] pathOf(int[] stopOrder, boolean returnToStart) {
        int[] path = new int[stopOrder.length + (returnToStart ? 2 : 1)];
        path[0] = 0;
        System.arraycopy(stopOrder, 0, path, 1, stopOrder.length);
        if (returnToStart) {
            path[path.length - 1] = 0;
        }
        return path;
    }

    private static int[] stopsInEnteredOrder(int stopCount) {
        int[] order = new int[stopCount];
        Arrays.setAll(order, index -> index + 1);
        return order;
    }

    private static List<Location> placesAlong(int[] path, List<Location> places) {
        return Arrays.stream(path).mapToObj(places::get).toList();
    }

    private List<RoutePlan.Leg> legsAlong(int[] path, List<Location> places, TravelMatrix matrix) {
        List<RoutePlan.Leg> legs = new ArrayList<>(path.length - 1);
        for (int step = 0; step < path.length - 1; step++) {
            Location from = places.get(path[step]);
            Location to = places.get(path[step + 1]);
            legs.add(new RoutePlan.Leg(
                    from.name(),
                    to.name(),
                    matrix.distanceKm(path[step], path[step + 1]),
                    // Only the legs that made it into the answer are drawn, so this costs one
                    // lookup per leg rather than one per pair.
                    travelMatrixProvider.pathBetween(from, to)));
        }
        return legs;
    }

    private static double distanceAlong(int[] path, TravelMatrix matrix) {
        double total = 0;
        for (int step = 0; step < path.length - 1; step++) {
            total += matrix.distanceKm(path[step], path[step + 1]);
        }
        return total;
    }
}
