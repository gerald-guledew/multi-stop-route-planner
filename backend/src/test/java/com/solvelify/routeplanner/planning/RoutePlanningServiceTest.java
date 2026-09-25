package com.solvelify.routeplanner.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.solvelify.routeplanner.distance.HaversineTravelMatrixProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The real provider and the real solver, because this is where the numbers have to be right.
 * Every figure below was calculated outside this codebase before the code existed.
 */
class RoutePlanningServiceTest {

    private static final Location SKY_TOWER = new Location("Sky Tower", -36.8485, 174.7621);
    private static final Location TAKAPUNA = new Location("Takapuna", -36.7870, 174.7740);
    private static final Location AIRPORT = new Location("Airport", -37.0082, 174.7850);
    private static final Location DEVONPORT = new Location("Devonport", -36.8330, 174.7955);
    private static final Location SYLVIA_PARK = new Location("Sylvia Park", -36.9170, 174.8414);

    private static final List<Location> STOPS = List.of(TAKAPUNA, AIRPORT, DEVONPORT, SYLVIA_PARK);

    private final RoutePlanningService service =
            new RoutePlanningService(new HaversineTravelMatrixProvider(), new BruteForceRouteSolver());

    @Test
    void findsTheShortestRoundTrip() {
        RoutePlan plan = service.plan(SKY_TOWER, STOPS, true);

        assertThat(plan.totalDistanceKm()).isCloseTo(51.76, within(0.01));
        assertThat(plan.enteredOrderDistanceKm()).isCloseTo(71.61, within(0.01));
        assertThat(plan.ordersChecked()).isEqualTo(24);
    }

    @Test
    void findsTheShortestOneWayTrip() {
        RoutePlan plan = service.plan(SKY_TOWER, STOPS, false);

        assertThat(plan.totalDistanceKm()).isCloseTo(33.89, within(0.01));
        assertThat(plan.enteredOrderDistanceKm()).isCloseTo(61.23, within(0.01));
        assertThat(names(plan))
                .containsExactly("Sky Tower", "Takapuna", "Devonport", "Sylvia Park", "Airport");
    }

    @Test
    void endsARoundTripWhereItStarted() {
        RoutePlan plan = service.plan(SKY_TOWER, STOPS, true);

        assertThat(names(plan)).startsWith("Sky Tower").endsWith("Sky Tower");
    }

    @Test
    void acceptsEitherDirectionOfTheSameRoundTrip() {
        // Driven backwards a round trip is exactly as long, so both answers are correct.
        List<String> forwards =
                List.of("Sky Tower", "Takapuna", "Devonport", "Sylvia Park", "Airport", "Sky Tower");
        List<String> backwards =
                List.of("Sky Tower", "Airport", "Sylvia Park", "Devonport", "Takapuna", "Sky Tower");

        RoutePlan plan = service.plan(SKY_TOWER, STOPS, true);

        assertThat(names(plan)).isIn(forwards, backwards);
    }

    @Test
    void reportsOneLegPerDrive() {
        RoutePlan roundTrip = service.plan(SKY_TOWER, STOPS, true);
        RoutePlan oneWay = service.plan(SKY_TOWER, STOPS, false);

        assertThat(roundTrip.legs()).hasSize(5);
        assertThat(oneWay.legs()).hasSize(4);
    }

    @Test
    void addsUpTheLegsToTheTotal() {
        RoutePlan plan = service.plan(SKY_TOWER, STOPS, true);

        double sumOfLegs = plan.legs().stream().mapToDouble(RoutePlan.Leg::distanceKm).sum();

        assertThat(sumOfLegs).isCloseTo(plan.totalDistanceKm(), within(0.000001));
    }

    private static List<String> names(RoutePlan plan) {
        return plan.route().stream().map(Location::name).toList();
    }
}
