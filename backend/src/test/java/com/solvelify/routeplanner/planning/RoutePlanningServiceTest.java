package com.solvelify.routeplanner.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.solvelify.routeplanner.distance.HaversineTravelMatrixProvider;
import java.util.List;
import java.util.Set;
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

    @Test
    void drawsNoRoadsWhenDistancesAreStraightLines() {
        RoutePlan plan = service.plan(SKY_TOWER, STOPS, true);

        // Two points already describe a straight line, so the map draws it without help.
        assertThat(plan.legs()).allSatisfy(leg -> assertThat(leg.path()).isEmpty());
    }

    // Stops kept in their place. The figures were worked out separately, as the others were.

    @Test
    void arrangesTheOtherStopsAroundOneKeptInItsPlace() {
        // The Airport was entered second and has to stay second.
        RoutePlan plan = service.plan(SKY_TOWER, STOPS, Set.of(1), true);

        assertThat(names(plan))
                .containsExactly("Sky Tower", "Sylvia Park", "Airport", "Devonport", "Takapuna", "Sky Tower");
        assertThat(plan.totalDistanceKm()).isCloseTo(53.58, within(0.01));
        assertThat(plan.ordersChecked()).isEqualTo(6);
    }

    @Test
    void keepsAStopInItsPlaceOnAOneWayTripToo() {
        RoutePlan plan = service.plan(SKY_TOWER, STOPS, Set.of(1), false);

        assertThat(names(plan)).containsExactly("Sky Tower", "Sylvia Park", "Airport", "Devonport", "Takapuna");
        assertThat(plan.totalDistanceKm()).isCloseTo(46.66, within(0.01));
    }

    @Test
    void stillComparesWithTheOrderEnteredWhenAStopIsKept() {
        RoutePlan plan = service.plan(SKY_TOWER, STOPS, Set.of(1), true);

        assertThat(plan.enteredOrderDistanceKm()).isCloseTo(71.61, within(0.01));
    }

    @Test
    void drivesTheOrderEnteredWhenEveryStopIsKept() {
        RoutePlan plan = service.plan(SKY_TOWER, STOPS, Set.of(0, 1, 2, 3), true);

        assertThat(names(plan))
                .containsExactly("Sky Tower", "Takapuna", "Airport", "Devonport", "Sylvia Park", "Sky Tower");
        assertThat(plan.totalDistanceKm()).isCloseTo(71.61, within(0.01));
        assertThat(plan.ordersChecked()).isEqualTo(1);
    }

    @Test
    void refusesToKeepAPositionThatHasNoStop() {
        assertThatThrownBy(() -> service.plan(SKY_TOWER, STOPS, Set.of(4), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only 4 stops");
    }

    private static List<String> names(RoutePlan plan) {
        return plan.route().stream().map(Location::name).toList();
    }

    // Driving times. A stand-in for the roads, with figures small enough to add up by hand.

    private static final Location HOME = new Location("Home", -36.87, 174.78);
    private static final Location OFFICE = new Location("Office", -36.85, 174.76);
    private static final Location SHOP = new Location("Shop", -36.90, 174.80);

    /**
     * Entered as Home, Office, Shop. Driven that way and back it is 11 + 5 + 20 = 36 km and
     * 100 + 100 + 100 = 300 seconds. The other way round, Home, Shop, Office and back, is
     * 20 + 5 + 10 = 35 km and 1500 + 300 + 600 = 2400 seconds. So the shortest order is not
     * the order entered, and not the quickest either.
     */
    private static final TravelMatrixProvider ROADS_WITH_TIMES = places -> new TravelMatrix(
            new double[][] {
                {0, 11, 20},
                {10, 0, 5},
                {20, 5, 0},
            },
            new double[][] {
                {0, 100, 1500},
                {600, 0, 100},
                {100, 300, 0},
            });

    private final RoutePlanningService onRoadsWithTimes =
            new RoutePlanningService(ROADS_WITH_TIMES, new BruteForceRouteSolver());

    @Test
    void saysHowLongEachLegOfTheChosenOrderTakes() {
        RoutePlan plan = onRoadsWithTimes.plan(HOME, List.of(OFFICE, SHOP), true);

        assertThat(names(plan)).containsExactly("Home", "Shop", "Office", "Home");
        assertThat(plan.legs()).extracting(leg -> leg.durationSeconds().orElseThrow())
                .containsExactly(1500.0, 300.0, 600.0);
    }

    @Test
    void addsTheLegsUpIntoTheTimeForTheWholeTrip() {
        RoutePlan plan = onRoadsWithTimes.plan(HOME, List.of(OFFICE, SHOP), true);

        assertThat(plan.totalDurationSeconds()).hasValue(2400);
    }

    @Test
    void leavesTheDriveHomeOutOfTheTimeForAOneWayTrip() {
        // One way, Home, Office, Shop is the shortest at 16 km, and takes 100 + 100 seconds.
        RoutePlan plan = onRoadsWithTimes.plan(HOME, List.of(OFFICE, SHOP), false);

        assertThat(names(plan)).containsExactly("Home", "Office", "Shop");
        assertThat(plan.totalDurationSeconds()).hasValue(200);
    }

    @Test
    void stillChoosesTheShortestOrderWhenAnotherWouldBeQuicker() {
        // The order entered takes 300 seconds against 2400, and is a kilometre longer.
        RoutePlan plan = onRoadsWithTimes.plan(HOME, List.of(OFFICE, SHOP), true);

        assertThat(plan.totalDistanceKm()).isEqualTo(35);
        assertThat(plan.enteredOrderDistanceKm()).isEqualTo(36);
        assertThat(plan.totalDurationSeconds()).hasValue(2400);
    }

    @Test
    void hasNoTimesToGiveOnStraightLines() {
        RoutePlan plan = service.plan(SKY_TOWER, STOPS, true);

        assertThat(plan.totalDurationSeconds()).isEmpty();
        assertThat(plan.legs()).allSatisfy(leg -> assertThat(leg.durationSeconds()).isEmpty());
    }

    // Climb and descent. The same three places and distances, on roads whose heights are known.

    /**
     * Home is 10 m up, the Office 60 m and the Shop 30 m. The road from Home to the Office goes
     * over a ridge 160 m high, so it climbs 150 m and comes down 100 m. The other two roads
     * slope one way only.
     */
    private static final TravelMatrixProvider ROADS_WITH_HEIGHTS = places -> new TravelMatrix(
            new double[][] {
                {0, 11, 20},
                {10, 0, 5},
                {20, 5, 0},
            },
            new double[][] {
                {0, 100, 1500},
                {600, 0, 100},
                {100, 300, 0},
            },
            new ClimbAndDescent[][] {
                {ClimbAndDescent.LEVEL, new ClimbAndDescent(150, 100), new ClimbAndDescent(20, 0)},
                {new ClimbAndDescent(100, 150), ClimbAndDescent.LEVEL, new ClimbAndDescent(0, 30)},
                {new ClimbAndDescent(0, 20), new ClimbAndDescent(30, 0), ClimbAndDescent.LEVEL},
            });

    private final RoutePlanningService onRoadsWithHeights =
            new RoutePlanningService(ROADS_WITH_HEIGHTS, new BruteForceRouteSolver());

    @Test
    void saysHowMuchEachLegOfTheChosenOrderClimbsAndDescends() {
        RoutePlan plan = onRoadsWithHeights.plan(HOME, List.of(OFFICE, SHOP), true);

        assertThat(names(plan)).containsExactly("Home", "Shop", "Office", "Home");
        assertThat(plan.legs()).extracting(leg -> leg.climbAndDescent().orElseThrow())
                .containsExactly(
                        new ClimbAndDescent(20, 0),
                        new ClimbAndDescent(30, 0),
                        new ClimbAndDescent(100, 150));
    }

    @Test
    void addsTheLegsUpIntoTheClimbAndTheDescentOfTheWholeTrip() {
        RoutePlan plan = onRoadsWithHeights.plan(HOME, List.of(OFFICE, SHOP), true);

        // A round trip ends where it began, so what it climbs it also comes down.
        assertThat(plan.totalClimbAndDescent()).contains(new ClimbAndDescent(150, 150));
    }

    @Test
    void leavesTheDriveHomeOutOfTheClimbForAOneWayTrip() {
        // One way the shortest is Home, Office, Shop: over the ridge, then down to the Shop.
        RoutePlan plan = onRoadsWithHeights.plan(HOME, List.of(OFFICE, SHOP), false);

        assertThat(names(plan)).containsExactly("Home", "Office", "Shop");
        // The Shop is 20 m higher than Home, which is the climb less the descent.
        assertThat(plan.totalClimbAndDescent()).contains(new ClimbAndDescent(150, 130));
    }

    @Test
    void stillChoosesTheShortestOrderWhenAnotherWouldClimbLess() {
        // Home, Shop, Office never meets the ridge and climbs 50 m. It is 9 km longer.
        RoutePlan plan = onRoadsWithHeights.plan(HOME, List.of(OFFICE, SHOP), false);

        assertThat(plan.totalDistanceKm()).isEqualTo(16);
        assertThat(plan.totalClimbAndDescent().orElseThrow().climbMetres()).isEqualTo(150);
    }

    @Test
    void stillGivesTheTimesWhenItAlsoHasTheClimb() {
        RoutePlan plan = onRoadsWithHeights.plan(HOME, List.of(OFFICE, SHOP), true);

        assertThat(plan.totalDurationSeconds()).hasValue(2400);
    }

    @Test
    void hasNoClimbToGiveWhenTheRoadsHaveNoHeights() {
        RoutePlan plan = onRoadsWithTimes.plan(HOME, List.of(OFFICE, SHOP), true);

        assertThat(plan.totalClimbAndDescent()).isEmpty();
        assertThat(plan.legs()).allSatisfy(leg -> assertThat(leg.climbAndDescent()).isEmpty());
    }

    @Test
    void hasNoClimbToGiveOnStraightLines() {
        RoutePlan plan = service.plan(SKY_TOWER, STOPS, true);

        assertThat(plan.totalClimbAndDescent()).isEmpty();
        assertThat(plan.legs()).allSatisfy(leg -> assertThat(leg.climbAndDescent()).isEmpty());
    }
}
