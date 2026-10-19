package com.solvelify.routeplanner.planning;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
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

    /**
     * How much an order found for time has to save over the shortest to be taken. The times are
     * for clear roads and the screen gives them to the minute, so a few seconds are no reason to
     * drive kilometres further.
     */
    static final double LEAST_SECONDS_SAVED = 60;

    private final TravelMatrixProvider travelMatrixProvider;
    private final RouteSolver routeSolver;
    private final PlanningProperties properties;

    public RoutePlanningService(
            TravelMatrixProvider travelMatrixProvider, RouteSolver routeSolver, PlanningProperties properties) {
        this.travelMatrixProvider = travelMatrixProvider;
        this.routeSolver = routeSolver;
        this.properties = properties;
    }

    /** Every stop free to move, and the order chosen for distance. */
    public RoutePlan plan(Location start, List<Location> stops, boolean returnToStart) {
        return plan(start, stops, Set.of(), returnToStart);
    }

    /** The order chosen for distance. */
    public RoutePlan plan(
            Location start, List<Location> stops, Set<Integer> keptInPlace, boolean returnToStart) {
        return plan(start, stops, keptInPlace, returnToStart, ChosenFor.DISTANCE);
    }

    /**
     * @param keptInPlace positions in {@code stops}, counting from 0, of the stops that must be
     *                    visited at the turn they were entered in. The first stop of the day
     *                    that cannot wait, say. The others are arranged around them
     * @param chosenFor   what the order is chosen for. Climb can be counted only on roads whose
     *                    heights are known, and time only on roads, since a straight line says
     *                    how far and not how long. Otherwise the order is chosen for distance,
     *                    whatever was asked, and the plan gives no shortest order to hold it
     *                    against. The fastest order is taken when it saves a minute or more, and
     *                    the shortest otherwise
     */
    public RoutePlan plan(
            Location start,
            List<Location> stops,
            Set<Integer> keptInPlace,
            boolean returnToStart,
            ChosenFor chosenFor) {

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
        Set<Integer> kept = asMatrixIndices(keptInPlace);

        // The solver is handed a table of costs and keeps the total down. Distances give the
        // shortest order. Step 6 fills the table with litres instead.
        RouteSolver.Solution shortest = routeSolver.solve(matrix.asCostMatrix(), returnToStart, kept);
        int[] shortestPath = pathOf(shortest.order(), returnToStart);

        // The same search again over another table, when the order is chosen for more than
        // distance. The shortest order is still worked out, because it is what this one is held
        // against.
        Optional<RouteSolver.Solution> chosen = costFor(chosenFor, matrix)
                .map(cost -> routeSolver.solve(cost, returnToStart, kept));

        int[] bestPath = chosen
                .map(solution -> pathOf(solution.order(), returnToStart))
                .filter(path -> savesEnough(chosenFor, path, shortestPath, matrix))
                .orElse(shortestPath);
        int[] enteredPath = pathOf(stopsInEnteredOrder(stops.size()), returnToStart);

        return new RoutePlan(
                placesAlong(bestPath, places),
                legsAlong(bestPath, places, matrix),
                // Added up along the road, and not the solver's total, which is a cost.
                distanceAlong(bestPath, matrix),
                durationAlong(bestPath, matrix),
                climbAlong(bestPath, matrix),
                distanceAlong(enteredPath, matrix),
                shortest.ordersChecked(),
                chosen.map(solution -> shortestOrderAlong(shortestPath, matrix)));
    }

    /**
     * The table an order chosen for more than distance is found with. Empty for distance alone,
     * which the shortest order already answers, and when the matrix lacks what the table needs:
     * heights for the climb, times for the time.
     */
    private Optional<double[][]> costFor(ChosenFor chosenFor, TravelMatrix matrix) {
        return switch (chosenFor) {
            case DISTANCE -> Optional.empty();
            case DISTANCE_AND_CLIMB -> matrix.asCostMatrixCountingClimb(properties.roadMetresPerMetreClimbed());
            case TIME -> matrix.asCostMatrixOfDrivingTime();
        };
    }

    /**
     * Whether an order found for time saves enough over the shortest to be taken. An order found
     * for anything else is taken as found.
     */
    private static boolean savesEnough(ChosenFor chosenFor, int[] path, int[] shortestPath, TravelMatrix matrix) {
        if (chosenFor != ChosenFor.TIME) {
            return true;
        }
        double saved = durationAlong(shortestPath, matrix).orElseThrow() - durationAlong(path, matrix).orElseThrow();
        return saved >= LEAST_SECONDS_SAVED;
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
                    matrix.durationSeconds(path[step], path[step + 1]),
                    matrix.climbAndDescent(path[step], path[step + 1]),
                    // Only the legs that made it into the answer are drawn, so this costs one
                    // lookup per leg rather than one per pair.
                    travelMatrixProvider.pathBetween(from, to)));
        }
        return legs;
    }

    /**
     * Driving time for the whole trip, or empty when the matrix has no times. Unless the order
     * was chosen for time, this says how long that order takes, not that it is the quickest.
     */
    private static OptionalDouble durationAlong(int[] path, TravelMatrix matrix) {
        double total = 0;
        for (int step = 0; step < path.length - 1; step++) {
            OptionalDouble leg = matrix.durationSeconds(path[step], path[step + 1]);
            if (leg.isEmpty()) {
                return OptionalDouble.empty();
            }
            total += leg.getAsDouble();
        }
        return OptionalDouble.of(total);
    }

    /**
     * Climb and descent for the whole trip, or empty when the matrix has none. Each is added up
     * leg by leg: the way down one leg takes nothing off the way up the next.
     */
    private static Optional<ClimbAndDescent> climbAlong(int[] path, TravelMatrix matrix) {
        ClimbAndDescent total = ClimbAndDescent.LEVEL;
        for (int step = 0; step < path.length - 1; step++) {
            Optional<ClimbAndDescent> leg = matrix.climbAndDescent(path[step], path[step + 1]);
            if (leg.isEmpty()) {
                return Optional.empty();
            }
            total = total.plus(leg.get());
        }
        return Optional.of(total);
    }

    private static RoutePlan.ShortestOrder shortestOrderAlong(int[] path, TravelMatrix matrix) {
        return new RoutePlan.ShortestOrder(
                distanceAlong(path, matrix), durationAlong(path, matrix), climbAlong(path, matrix));
    }

    private static double distanceAlong(int[] path, TravelMatrix matrix) {
        double total = 0;
        for (int step = 0; step < path.length - 1; step++) {
            total += matrix.distanceKm(path[step], path[step + 1]);
        }
        return total;
    }
}
