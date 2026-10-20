package com.solvelify.routeplanner.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.solvelify.routeplanner.distance.HaversineTravelMatrixProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
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

    /** The settings as shipped: a metre climbed counts as 10 m of road. */
    private static final PlanningProperties AS_SHIPPED = new PlanningProperties(null);

    private final RoutePlanningService service = new RoutePlanningService(
            new HaversineTravelMatrixProvider(), new BruteForceRouteSolver(), AS_SHIPPED);

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
    private static final TravelMatrixProvider ROADS_WITH_TIMES = (places, roads) -> new TravelMatrix(
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
            new RoutePlanningService(ROADS_WITH_TIMES, new BruteForceRouteSolver(), AS_SHIPPED);

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
    private static final TravelMatrixProvider ROADS_WITH_HEIGHTS = (places, roads) -> new TravelMatrix(
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
            new RoutePlanningService(ROADS_WITH_HEIGHTS, new BruteForceRouteSolver(), AS_SHIPPED);

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

    // An order chosen for distance and climb together.

    private static final List<Location> OFFICE_AND_SHOP = List.of(OFFICE, SHOP);
    private static final Set<Integer> NONE_KEPT = Set.of();
    private static final boolean ONE_WAY = false;
    private static final boolean THERE_AND_BACK = true;

    /**
     * The same three places and the same hills, with the Shop nearer Home. One way, Home,
     * Office, Shop is 11 + 5 = 16 km and goes over the ridge, climbing 150 m. Home, Shop, Office
     * is 11.5 + 5 = 16.5 km and climbs 20 + 30 = 50 m. So the shortest order climbs 100 m more
     * to be half a kilometre shorter.
     *
     * <p>There and back is another matter. Home, Office, Shop, Home is 11 + 5 + 12 = 28 km and
     * the other way round is 11.5 + 5 + 14 = 30.5 km. Both climb 150 m, as a round trip over the
     * same roads must, whichever way it is driven.
     */
    private static final TravelMatrixProvider A_RIDGE_ON_THE_SHORT_WAY = (places, roads) -> new TravelMatrix(
            new double[][] {
                {0, 11, 11.5},
                {14, 0, 5},
                {12, 5, 0},
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

    private final RoutePlanningService overTheRidge =
            new RoutePlanningService(A_RIDGE_ON_THE_SHORT_WAY, new BruteForceRouteSolver(), AS_SHIPPED);

    @Test
    void goesALittleFurtherToClimbAGoodDealLess() {
        // The 100 m of climb saved count as a kilometre of road. The way round is half of one.
        RoutePlan plan = overTheRidge.plan(
                HOME, OFFICE_AND_SHOP, NONE_KEPT, ONE_WAY, ChosenFor.DISTANCE_AND_CLIMB);

        assertThat(names(plan)).containsExactly("Home", "Shop", "Office");
        assertThat(plan.totalClimbAndDescent()).contains(new ClimbAndDescent(50, 0));
    }

    @Test
    void saysHowFarTheOrderGoesAndNotWhatItCameToWithTheClimbCounted() {
        RoutePlan plan = overTheRidge.plan(
                HOME, OFFICE_AND_SHOP, NONE_KEPT, ONE_WAY, ChosenFor.DISTANCE_AND_CLIMB);

        // 16.5 km of road. With the climb counted it came to 17.
        assertThat(plan.totalDistanceKm()).isEqualTo(16.5);
        assertThat(plan.legs()).extracting(RoutePlan.Leg::distanceKm).containsExactly(11.5, 5.0);
    }

    @Test
    void saysWhatTheShortestOrderWouldHaveComeTo() {
        RoutePlan plan = overTheRidge.plan(
                HOME, OFFICE_AND_SHOP, NONE_KEPT, ONE_WAY, ChosenFor.DISTANCE_AND_CLIMB);

        assertThat(plan.shortestOrder()).contains(new RoutePlan.ShortestOrder(
                16, OptionalDouble.of(200), Optional.of(new ClimbAndDescent(150, 130))));
    }

    @Test
    void takesTheShortestOrderUnlessAskedToCountTheClimb() {
        RoutePlan forDistance = overTheRidge.plan(
                HOME, OFFICE_AND_SHOP, NONE_KEPT, ONE_WAY, ChosenFor.DISTANCE);
        RoutePlan nothingSaid = overTheRidge.plan(HOME, OFFICE_AND_SHOP, NONE_KEPT, ONE_WAY);

        assertThat(names(forDistance)).containsExactly("Home", "Office", "Shop");
        assertThat(forDistance.totalDistanceKm()).isEqualTo(16);
        // Nothing to hold it against. It is the shortest order.
        assertThat(forDistance.shortestOrder()).isEmpty();
        assertThat(nothingSaid).isEqualTo(forDistance);
    }

    @Test
    void countsTheClimbOfTheDriveHomeAsWell() {
        // There and back both orders climb 150 m, so the shorter one stands. Judged on the way
        // out alone, the order would have changed.
        RoutePlan plan = overTheRidge.plan(
                HOME, OFFICE_AND_SHOP, NONE_KEPT, THERE_AND_BACK, ChosenFor.DISTANCE_AND_CLIMB);

        assertThat(names(plan)).containsExactly("Home", "Office", "Shop", "Home");
        assertThat(plan.totalDistanceKm()).isEqualTo(28);
    }

    @Test
    void givesTheSameFiguresTwiceWhenTheShortestOrderStands() {
        RoutePlan plan = overTheRidge.plan(
                HOME, OFFICE_AND_SHOP, NONE_KEPT, THERE_AND_BACK, ChosenFor.DISTANCE_AND_CLIMB);

        assertThat(plan.totalClimbAndDescent()).contains(new ClimbAndDescent(150, 150));
        assertThat(plan.shortestOrder()).contains(new RoutePlan.ShortestOrder(
                28, OptionalDouble.of(300), Optional.of(new ClimbAndDescent(150, 150))));
    }

    @Test
    void doesNotGoFarOutOfItsWayForTheClimb() {
        // On the roads further up, going by the Shop first is 9 km longer. The 100 m of climb
        // it saves count as one.
        RoutePlan plan = onRoadsWithHeights.plan(
                HOME, OFFICE_AND_SHOP, NONE_KEPT, ONE_WAY, ChosenFor.DISTANCE_AND_CLIMB);

        assertThat(names(plan)).containsExactly("Home", "Office", "Shop");
        assertThat(plan.totalDistanceKm()).isEqualTo(16);
    }

    @Test
    void leavesAKeptStopAtItsTurnWhenCountingTheClimb() {
        // With the Office kept first there is one order, and it goes over the ridge.
        RoutePlan plan = overTheRidge.plan(
                HOME, OFFICE_AND_SHOP, Set.of(0), ONE_WAY, ChosenFor.DISTANCE_AND_CLIMB);

        assertThat(names(plan)).containsExactly("Home", "Office", "Shop");
        assertThat(plan.ordersChecked()).isEqualTo(1);
    }

    @Test
    void countsAMetreClimbedAsManyMetresOfRoadAsItIsSetTo() {
        // At 4 m of road each, the 100 m saved count as 400 m. The way round is 500 m.
        RoutePlanningService countingLessForTheClimb = new RoutePlanningService(
                A_RIDGE_ON_THE_SHORT_WAY, new BruteForceRouteSolver(), new PlanningProperties(4.0));

        RoutePlan plan = countingLessForTheClimb.plan(
                HOME, OFFICE_AND_SHOP, NONE_KEPT, ONE_WAY, ChosenFor.DISTANCE_AND_CLIMB);

        assertThat(names(plan)).containsExactly("Home", "Office", "Shop");
        // The climb was counted, though it changed nothing, so the shortest order is given.
        assertThat(plan.shortestOrder()).isPresent();
    }

    @Test
    void choosesForDistanceWhenTheRoadsHaveNoHeights() {
        RoutePlan plan = onRoadsWithTimes.plan(
                HOME, OFFICE_AND_SHOP, NONE_KEPT, ONE_WAY, ChosenFor.DISTANCE_AND_CLIMB);

        assertThat(names(plan)).containsExactly("Home", "Office", "Shop");
        assertThat(plan.totalDistanceKm()).isEqualTo(16);
        // No climb was counted, and giving no shortest order is how the plan says so.
        assertThat(plan.shortestOrder()).isEmpty();
    }

    @Test
    void choosesForDistanceOnStraightLinesWhateverWasAsked() {
        RoutePlan plan = service.plan(SKY_TOWER, STOPS, NONE_KEPT, THERE_AND_BACK, ChosenFor.DISTANCE_AND_CLIMB);

        assertThat(plan.totalDistanceKm()).isCloseTo(51.76, within(0.01));
        assertThat(plan.shortestOrder()).isEmpty();
    }

    // The roads between the stops, when the climb is counted.

    /**
     * The roads over the ridge, and other roads that climb less: from Home to the Office one goes
     * round the ridge, 11.3 km climbing 60 m and coming down 10. Every other drive is the same.
     *
     * <p>One way, Home, Office, Shop is then 11.3 + 5 = 16.3 km and climbs 60 m, which with the
     * climb counted comes to 16.9. Home, Shop, Office still comes to 17. So the order stays as
     * entered, and the road round the ridge is taken.
     */
    private static final TravelMatrixProvider ROUND_THE_RIDGE = (places, roads) -> new TravelMatrix(
            new double[][] {
                {0, 11.3, 11.5},
                {14, 0, 5},
                {12, 5, 0},
            },
            new double[][] {
                {0, 120, 1500},
                {600, 0, 100},
                {100, 300, 0},
            },
            new ClimbAndDescent[][] {
                {ClimbAndDescent.LEVEL, new ClimbAndDescent(60, 10), new ClimbAndDescent(20, 0)},
                {new ClimbAndDescent(100, 150), ClimbAndDescent.LEVEL, new ClimbAndDescent(0, 30)},
                {new ClimbAndDescent(0, 20), new ClimbAndDescent(30, 0), ClimbAndDescent.LEVEL},
            });

    /** A road on the map, drawn by which roads it was asked along. */
    private static final GeoPoint ON_THE_USUAL_ROADS = new GeoPoint(0, 0);
    private static final GeoPoint ON_THE_ROADS_CLIMBING_LESS = new GeoPoint(1, 1);

    /** One provider for the usual roads and another for those that climb less, and what was asked. */
    private static final class TwoKindsOfRoads implements TravelMatrixProvider {

        private final TravelMatrixProvider usual;
        private final TravelMatrixProvider climbingLess;
        private final List<Roads> asked = new ArrayList<>();

        TwoKindsOfRoads(TravelMatrixProvider usual, TravelMatrixProvider climbingLess) {
            this.usual = usual;
            this.climbingLess = climbingLess;
        }

        @Override
        public TravelMatrix matrixFor(List<Location> places, Roads roads) {
            asked.add(roads);
            return (roads == Roads.USUAL ? usual : climbingLess).matrixFor(places, roads);
        }

        @Override
        public List<GeoPoint> pathBetween(Location from, Location to, Roads roads) {
            return List.of(roads == Roads.USUAL ? ON_THE_USUAL_ROADS : ON_THE_ROADS_CLIMBING_LESS);
        }
    }

    private final TwoKindsOfRoads roundTheRidge = new TwoKindsOfRoads(A_RIDGE_ON_THE_SHORT_WAY, ROUND_THE_RIDGE);

    private RoutePlan countingTheClimb(TravelMatrixProvider roads, ChosenFor chosenFor) {
        return new RoutePlanningService(roads, new BruteForceRouteSolver(), AS_SHIPPED)
                .plan(HOME, OFFICE_AND_SHOP, NONE_KEPT, ONE_WAY, chosenFor);
    }

    @Test
    void takesTheRoadRoundTheRidgeWhenCountingTheClimb() {
        RoutePlan plan = countingTheClimb(roundTheRidge, ChosenFor.DISTANCE_AND_CLIMB);

        assertThat(names(plan)).containsExactly("Home", "Office", "Shop");
        assertThat(plan.legs()).extracting(RoutePlan.Leg::distanceKm).containsExactly(11.3, 5.0);
        assertThat(plan.totalDistanceKm()).isCloseTo(16.3, within(1e-9));
        assertThat(plan.totalDurationSeconds()).hasValue(220);
        assertThat(plan.totalClimbAndDescent()).contains(new ClimbAndDescent(60, 40));
    }

    @Test
    void drawsEachLegAlongTheRoadsItsDriveFollowed() {
        RoutePlan plan = countingTheClimb(roundTheRidge, ChosenFor.DISTANCE_AND_CLIMB);

        // From the Office to the Shop both roads are the same, and the usual one is kept.
        assertThat(plan.legs()).extracting(RoutePlan.Leg::path)
                .containsExactly(List.of(ON_THE_ROADS_CLIMBING_LESS), List.of(ON_THE_USUAL_ROADS));
    }

    /**
     * Roads that climb less by the engine's count, and not by the planner's. The engine counts
     * every rise along a road, and the planner evens the heights out first. From Home to the
     * Office this road is 12 km and climbs 155 m by the planner's count, where the road over the
     * ridge is 11 km and climbs 150 m.
     */
    private static final TravelMatrixProvider LONGER_AND_NO_LOWER = (places, roads) -> new TravelMatrix(
            new double[][] {
                {0, 12, 11.5},
                {14, 0, 5},
                {12, 5, 0},
            },
            new double[][] {
                {0, 130, 1500},
                {600, 0, 100},
                {100, 300, 0},
            },
            new ClimbAndDescent[][] {
                {ClimbAndDescent.LEVEL, new ClimbAndDescent(155, 105), new ClimbAndDescent(20, 0)},
                {new ClimbAndDescent(100, 150), ClimbAndDescent.LEVEL, new ClimbAndDescent(0, 30)},
                {new ClimbAndDescent(0, 20), new ClimbAndDescent(30, 0), ClimbAndDescent.LEVEL},
            });

    /**
     * From Home to the Office a road 10.5 km long that climbs 145 m: shorter than the road over
     * the ridge, and 5 m lower, which is less than the heights can tell.
     */
    private static final TravelMatrixProvider SHORTER_AND_ALMOST_AS_HIGH = (places, roads) -> new TravelMatrix(
            new double[][] {
                {0, 10.5, 11.5},
                {14, 0, 5},
                {12, 5, 0},
            },
            new double[][] {
                {0, 100, 1500},
                {600, 0, 100},
                {100, 300, 0},
            },
            new ClimbAndDescent[][] {
                {ClimbAndDescent.LEVEL, new ClimbAndDescent(145, 95), new ClimbAndDescent(20, 0)},
                {new ClimbAndDescent(100, 150), ClimbAndDescent.LEVEL, new ClimbAndDescent(0, 30)},
                {new ClimbAndDescent(0, 20), new ClimbAndDescent(30, 0), ClimbAndDescent.LEVEL},
            });

    @Test
    void changesRoadsOnlyForTenMetresOfClimbOrMore() {
        RoutePlan plan = countingTheClimb(
                new TwoKindsOfRoads(A_RIDGE_ON_THE_SHORT_WAY, SHORTER_AND_ALMOST_AS_HIGH), ChosenFor.DISTANCE_AND_CLIMB);

        assertThat(names(plan)).containsExactly("Home", "Shop", "Office");
        assertThat(plan.legs()).allSatisfy(leg -> assertThat(leg.path()).containsExactly(ON_THE_USUAL_ROADS));
    }

    @Test
    void keepsTheUsualRoadWhereTheOtherComesOutBehindByThePlannersCount() {
        RoutePlan plan = countingTheClimb(
                new TwoKindsOfRoads(A_RIDGE_ON_THE_SHORT_WAY, LONGER_AND_NO_LOWER), ChosenFor.DISTANCE_AND_CLIMB);

        // As over the ridge alone: Home, Shop, Office, 16.5 km climbing 50 m.
        assertThat(names(plan)).containsExactly("Home", "Shop", "Office");
        assertThat(plan.totalDistanceKm()).isEqualTo(16.5);
        assertThat(plan.legs()).allSatisfy(leg -> assertThat(leg.path()).containsExactly(ON_THE_USUAL_ROADS));
    }

    @Test
    void holdsThePlanAgainstTheShortestOrderOnTheUsualRoads() {
        RoutePlan plan = countingTheClimb(roundTheRidge, ChosenFor.DISTANCE_AND_CLIMB);

        // Over the ridge: 16 km, climbing 150 m.
        assertThat(plan.shortestOrder()).contains(new RoutePlan.ShortestOrder(
                16, OptionalDouble.of(200), Optional.of(new ClimbAndDescent(150, 130))));
    }

    @Test
    void measuresTheOrderEnteredAlongTheSameRoads() {
        RoutePlan plan = countingTheClimb(roundTheRidge, ChosenFor.DISTANCE_AND_CLIMB);

        // Entered as Home, Office, Shop, the order taken. Round the ridge it is 16.3 km.
        assertThat(plan.enteredOrderDistanceKm()).isEqualTo(plan.totalDistanceKm());
    }

    @Test
    void asksForTheRoadsThatClimbLessOnlyWhenCountingTheClimb() {
        countingTheClimb(roundTheRidge, ChosenFor.DISTANCE);
        countingTheClimb(roundTheRidge, ChosenFor.TIME);
        assertThat(roundTheRidge.asked).containsExactly(Roads.USUAL, Roads.USUAL);

        countingTheClimb(roundTheRidge, ChosenFor.DISTANCE_AND_CLIMB);
        assertThat(roundTheRidge.asked).endsWith(Roads.USUAL, Roads.CLIMBING_LESS);
    }

    @Test
    void keepsToTheUsualRoadsWhenTheirHeightsAreNotKnown() {
        TwoKindsOfRoads withoutHeights = new TwoKindsOfRoads(ROADS_WITH_TIMES, ROUND_THE_RIDGE);

        RoutePlan plan = countingTheClimb(withoutHeights, ChosenFor.DISTANCE_AND_CLIMB);

        assertThat(withoutHeights.asked).containsExactly(Roads.USUAL);
        assertThat(plan.legs()).allSatisfy(leg -> assertThat(leg.path()).containsExactly(ON_THE_USUAL_ROADS));
    }

    @Test
    void drawsTheLegsAlongTheUsualRoadsWhenTheClimbIsNotCounted() {
        RoutePlan plan = countingTheClimb(roundTheRidge, ChosenFor.DISTANCE);

        assertThat(plan.legs()).allSatisfy(leg -> assertThat(leg.path()).containsExactly(ON_THE_USUAL_ROADS));
        assertThat(plan.totalDistanceKm()).isEqualTo(16);
    }

    // An order chosen for driving time.

    /**
     * Home to the Office is 10 km through town and takes 15 minutes. Home to the Shop is 12 km by
     * motorway and takes as long as given. Office and Shop are 5 km and 5 minutes apart either way,
     * and the drives home are 10 km and 11 km, 10 minutes each.
     *
     * <p>There and back, the order entered, Home, Office, Shop, is 10 + 5 + 11 = 26 km and
     * 900 + 300 + 600 = 1800 seconds. Home, Shop, Office is 12 + 5 + 10 = 27 km, and with
     * 10 minutes to the Shop, 600 + 300 + 600 = 1500 seconds. So the shortest order is then five
     * minutes slower.
     */
    private static TravelMatrixProvider aMotorwayToTheShopTaking(double seconds) {
        return (places, roads) -> new TravelMatrix(
                new double[][] {
                    {0, 10, 12},
                    {10, 0, 5},
                    {11, 5, 0},
                },
                new double[][] {
                    {0, 900, seconds},
                    {600, 0, 300},
                    {600, 300, 0},
                });
    }

    private final RoutePlanningService byTheMotorway =
            new RoutePlanningService(aMotorwayToTheShopTaking(600), new BruteForceRouteSolver(), AS_SHIPPED);

    @Test
    void goesFurtherToTakeLessTime() {
        RoutePlan plan = byTheMotorway.plan(HOME, OFFICE_AND_SHOP, NONE_KEPT, THERE_AND_BACK, ChosenFor.TIME);

        assertThat(names(plan)).containsExactly("Home", "Shop", "Office", "Home");
        assertThat(plan.totalDurationSeconds()).hasValue(1500);
        assertThat(plan.totalDistanceKm()).isEqualTo(27);
    }

    @Test
    void saysWhatTheShortestOrderWouldHaveComeToAndHowLongItTakes() {
        RoutePlan plan = byTheMotorway.plan(HOME, OFFICE_AND_SHOP, NONE_KEPT, THERE_AND_BACK, ChosenFor.TIME);

        // No heights on these roads, so no climb to give.
        assertThat(plan.shortestOrder()).contains(
                new RoutePlan.ShortestOrder(26, OptionalDouble.of(1800), Optional.empty()));
    }

    @Test
    void takesTheShortestOrderUnlessAskedForTime() {
        RoutePlan plan = byTheMotorway.plan(HOME, OFFICE_AND_SHOP, NONE_KEPT, THERE_AND_BACK, ChosenFor.DISTANCE);

        assertThat(names(plan)).containsExactly("Home", "Office", "Shop", "Home");
        assertThat(plan.totalDurationSeconds()).hasValue(1800);
        assertThat(plan.shortestOrder()).isEmpty();
    }

    @Test
    void choosesForTimeOnAOneWayTripToo() {
        // One way, Home, Shop, Office is 17 km in 900 seconds, and the shortest 15 km in 1200.
        RoutePlan plan = byTheMotorway.plan(HOME, OFFICE_AND_SHOP, NONE_KEPT, ONE_WAY, ChosenFor.TIME);

        assertThat(names(plan)).containsExactly("Home", "Shop", "Office");
        assertThat(plan.totalDurationSeconds()).hasValue(900);
        assertThat(plan.shortestOrder().orElseThrow().durationSeconds()).hasValue(1200);
    }

    @Test
    void leavesAKeptStopAtItsTurnWhenChoosingForTime() {
        // With the Office kept first there is one order, and it is the slower one.
        RoutePlan plan = byTheMotorway.plan(HOME, OFFICE_AND_SHOP, Set.of(0), THERE_AND_BACK, ChosenFor.TIME);

        assertThat(names(plan)).containsExactly("Home", "Office", "Shop", "Home");
        assertThat(plan.ordersChecked()).isEqualTo(1);
    }

    @Test
    void givesTheSameFiguresTwiceWhenTheShortestOrderIsAlsoTheQuickest() {
        // One way on the roads with times further up, Home, Office, Shop is both: 16 km in 200
        // seconds, against 25 km in 1800.
        RoutePlan plan = onRoadsWithTimes.plan(HOME, OFFICE_AND_SHOP, NONE_KEPT, ONE_WAY, ChosenFor.TIME);

        assertThat(names(plan)).containsExactly("Home", "Office", "Shop");
        assertThat(plan.shortestOrder()).contains(
                new RoutePlan.ShortestOrder(16, OptionalDouble.of(200), Optional.empty()));
    }

    @Test
    void givesTheClimbOfTheShortestOrderWhenTheRoadsHaveHeights() {
        // There and back, the shortest is Home, Shop, Office in 2400 seconds. The quickest is
        // the other way round, in 300. Both climb 150 m.
        RoutePlan plan = onRoadsWithHeights.plan(HOME, OFFICE_AND_SHOP, NONE_KEPT, THERE_AND_BACK, ChosenFor.TIME);

        assertThat(names(plan)).containsExactly("Home", "Office", "Shop", "Home");
        assertThat(plan.shortestOrder()).contains(new RoutePlan.ShortestOrder(
                35, OptionalDouble.of(2400), Optional.of(new ClimbAndDescent(150, 150))));
    }

    @Test
    void keepsTheShortestOrderWhenNoOtherSavesAMinute() {
        // With 870 seconds to the Shop, going there first takes 1770 seconds. Half a minute less
        // is no reason to drive a kilometre further.
        RoutePlanningService halfAMinuteQuicker = new RoutePlanningService(
                aMotorwayToTheShopTaking(870), new BruteForceRouteSolver(), AS_SHIPPED);

        RoutePlan plan = halfAMinuteQuicker.plan(HOME, OFFICE_AND_SHOP, NONE_KEPT, THERE_AND_BACK, ChosenFor.TIME);

        assertThat(names(plan)).containsExactly("Home", "Office", "Shop", "Home");
        // Still held against the shortest order, which it is.
        assertThat(plan.shortestOrder()).contains(
                new RoutePlan.ShortestOrder(26, OptionalDouble.of(1800), Optional.empty()));
    }

    @Test
    void takesAnOrderThatSavesAMinute() {
        RoutePlanningService aMinuteQuicker = new RoutePlanningService(
                aMotorwayToTheShopTaking(840), new BruteForceRouteSolver(), AS_SHIPPED);

        RoutePlan plan = aMinuteQuicker.plan(HOME, OFFICE_AND_SHOP, NONE_KEPT, THERE_AND_BACK, ChosenFor.TIME);

        assertThat(names(plan)).containsExactly("Home", "Shop", "Office", "Home");
        assertThat(plan.totalDurationSeconds()).hasValue(1740);
    }

    @Test
    void choosesForDistanceOnStraightLinesWhenAskedForTime() {
        // A straight line says how far and not how long, so there is no time to choose by.
        RoutePlan plan = service.plan(SKY_TOWER, STOPS, NONE_KEPT, THERE_AND_BACK, ChosenFor.TIME);

        assertThat(plan.totalDistanceKm()).isCloseTo(51.76, within(0.01));
        assertThat(plan.shortestOrder()).isEmpty();
    }
}
