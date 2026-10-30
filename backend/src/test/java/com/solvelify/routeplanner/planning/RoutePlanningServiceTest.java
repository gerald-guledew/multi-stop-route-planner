package com.solvelify.routeplanner.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.solvelify.routeplanner.distance.HaversineTravelMatrixProvider;
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

    private final RoutePlanningService service = new RoutePlanningService(
            new HaversineTravelMatrixProvider(), new BruteForceRouteSolver());

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
    void keepsAStopOverBetweenTheStopsEitherSideOfIt() {
        // Free, the trip goes Takapuna, Devonport, Sylvia Park, Airport.
        RoutePlan plan = service.plan(
                SKY_TOWER, List.of(DEVONPORT, AIRPORT, SYLVIA_PARK, TAKAPUNA), Set.of(), Set.of(1), false,
                ChosenFor.DISTANCE, Optional.empty());

        List<String> names = names(plan);
        assertThat(names.indexOf("Airport")).isEqualTo(names.indexOf("Devonport") + 1);
        assertThat(names.indexOf("Sylvia Park")).isEqualTo(names.indexOf("Airport") + 1);
    }

    @Test
    void comparesAStopOverWithTheOrderEnteredAsAnyOtherPlan() {
        RoutePlan free = service.plan(SKY_TOWER, STOPS, false);
        RoutePlan held = service.plan(
                SKY_TOWER, STOPS, Set.of(), Set.of(1), false, ChosenFor.DISTANCE, Optional.empty());

        assertThat(held.enteredOrderDistanceKm()).isEqualTo(free.enteredOrderDistanceKm());
        assertThat(held.totalDistanceKm()).isGreaterThanOrEqualTo(free.totalDistanceKm());
        assertThat(held.ordersChecked()).isLessThan(free.ordersChecked());
    }

    @Test
    void refusesAStopOverThatIsNotInTheList() {
        assertThatThrownBy(() -> service.plan(
                        SKY_TOWER, STOPS, Set.of(), Set.of(4), false, ChosenFor.DISTANCE, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("position 4");
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

    // The same three places, on trips that keep a stop or not, one way or there and back.

    private static final List<Location> OFFICE_AND_SHOP = List.of(OFFICE, SHOP);
    private static final Set<Integer> NONE_KEPT = Set.of();
    private static final boolean ONE_WAY = false;
    private static final boolean THERE_AND_BACK = true;

    // The road of each leg, and its turns.

    /** A road on the map and a turn onto it, the same for every drive. */
    private static final GeoPoint ON_THE_ROAD = new GeoPoint(-36.86, 174.77);
    private static final Turn ONTO_THE_ROAD = Turn.onto(Turn.Way.LEFT, "Usual Road", 100);

    /** The roads with times, and how to drive them. */
    private static final TravelMatrixProvider ROADS_DRAWN = new TravelMatrixProvider() {
        @Override
        public TravelMatrix matrixFor(List<Location> places) {
            return ROADS_WITH_TIMES.matrixFor(places);
        }

        @Override
        public Directions directionsBetween(Location from, Location to) {
            return new Directions(List.of(ON_THE_ROAD), List.of(ONTO_THE_ROAD));
        }
    };

    private final RoutePlanningService onRoadsDrawn = new RoutePlanningService(ROADS_DRAWN, new BruteForceRouteSolver());

    @Test
    void drawsEachLegAlongItsRoad() {
        RoutePlan plan = onRoadsDrawn.plan(HOME, OFFICE_AND_SHOP, ONE_WAY);

        assertThat(plan.legs()).extracting(RoutePlan.Leg::path)
                .containsExactly(List.of(ON_THE_ROAD), List.of(ON_THE_ROAD));
    }

    @Test
    void writesOutTheTurnsOfEachLeg() {
        RoutePlan plan = onRoadsDrawn.plan(HOME, OFFICE_AND_SHOP, ONE_WAY);

        assertThat(plan.legs()).extracting(RoutePlan.Leg::turns)
                .containsExactly(List.of(ONTO_THE_ROAD), List.of(ONTO_THE_ROAD));
    }

    @Test
    void hasNoTurnsToWriteOutOnStraightLines() {
        RoutePlan plan = service.plan(SKY_TOWER, STOPS, true);

        assertThat(plan.legs()).allSatisfy(leg -> assertThat(leg.turns()).isEmpty());
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
        return places -> new TravelMatrix(
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
            new RoutePlanningService(aMotorwayToTheShopTaking(600), new BruteForceRouteSolver());

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
                aMotorwayToTheShopTaking(870), new BruteForceRouteSolver());

        RoutePlan plan = halfAMinuteQuicker.plan(HOME, OFFICE_AND_SHOP, NONE_KEPT, THERE_AND_BACK, ChosenFor.TIME);

        assertThat(names(plan)).containsExactly("Home", "Office", "Shop", "Home");
        // Still held against the shortest order, which it is.
        assertThat(plan.shortestOrder()).contains(
                new RoutePlan.ShortestOrder(26, OptionalDouble.of(1800), Optional.empty()));
    }

    @Test
    void takesAnOrderThatSavesAMinute() {
        RoutePlanningService aMinuteQuicker = new RoutePlanningService(
                aMotorwayToTheShopTaking(840), new BruteForceRouteSolver());

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

    // An order chosen for the least fuel.

    /** A car of 1,225 kg at 7.5 L/100 km. With the driver it weighs 1,300 kg. */
    private static final Optional<FuelUse> A_MEDIUM_CAR = Optional.of(new FuelUse(7.5, OptionalDouble.of(1225)));

    private static final ClimbAndDescent THREE_HUNDRED_METRES_UP = new ClimbAndDescent(300, 0);

    private static ClimbAndDescent down(double metres, double overMetres) {
        return new ClimbAndDescent(0, metres, List.of(new ClimbAndDescent.Descent(metres, overMetres)));
    }

    /**
     * The Office is on a hill 300 m above Home and the Shop, which are 2 km apart on the level.
     * Up from Home is 4 km and up from the Shop 2 km. Down to the Shop is 2 km, at 15%, and down
     * to Home is as long as given.
     *
     * <p>There and back, Home, Office, Shop is the shortest at 4 + 2 + 2 = 8 km. Climbing the
     * hill takes 0.39 L in the car above, and the steep way down to the Shop gives back only the
     * 0.15 L its 2 km take on the level. So the trip takes 0.6 + 0.39 - 0.15 = 0.84 L. Home, Shop,
     * Office is 2 + 2 km and the way down to Home. That way at 5 km gives back 0.375 L, and the
     * trip takes 0.675 + 0.39 - 0.375 = 0.69 L. At 6 km it gives back the whole climb, and the
     * trip takes 0.75 L.
     */
    private static TravelMatrixProvider aHillWithTheWayDownToHome(double km) {
        return places -> new TravelMatrix(
                new double[][] {
                    {0, 4, 2},
                    {km, 0, 2},
                    {2, 2, 0},
                },
                new double[][] {
                    {0, 400, 120},
                    {km * 60, 0, 200},
                    {120, 200, 0},
                },
                new ClimbAndDescent[][] {
                    {ClimbAndDescent.LEVEL, THREE_HUNDRED_METRES_UP, ClimbAndDescent.LEVEL},
                    {down(300, km * 1000), ClimbAndDescent.LEVEL, down(300, 2000)},
                    {ClimbAndDescent.LEVEL, THREE_HUNDRED_METRES_UP, ClimbAndDescent.LEVEL},
                });
    }

    private static RoutePlan forFuel(TravelMatrixProvider roads, boolean returnToStart, Optional<FuelUse> vehicle) {
        return new RoutePlanningService(roads, new BruteForceRouteSolver())
                .plan(HOME, OFFICE_AND_SHOP, NONE_KEPT, returnToStart, ChosenFor.FUEL, vehicle);
    }

    @Test
    void goesFurtherToKeepOffASteepDescent() {
        // 0.15 L less, for a kilometre more.
        RoutePlan plan = forFuel(aHillWithTheWayDownToHome(5), THERE_AND_BACK, A_MEDIUM_CAR);

        assertThat(names(plan)).containsExactly("Home", "Shop", "Office", "Home");
        assertThat(plan.totalDistanceKm()).isEqualTo(9);
    }

    @Test
    void saysWhatTheShortestOrderComesToWhenChoosingForFuel() {
        RoutePlan plan = forFuel(aHillWithTheWayDownToHome(5), THERE_AND_BACK, A_MEDIUM_CAR);

        assertThat(plan.shortestOrder()).contains(new RoutePlan.ShortestOrder(
                8, OptionalDouble.of(720), Optional.of(THREE_HUNDRED_METRES_UP.plus(down(300, 2000)))));
    }

    @Test
    void keepsTheShortestOrderWhenNoOtherSavesAHundredthOfALitre() {
        // With 7.1 km down to Home, the other way round gives back the whole climb and takes the
        // 11.1 km on the level: 0.8325 L, under a hundredth less, for 3.1 km more.
        RoutePlan plan = forFuel(aHillWithTheWayDownToHome(7.1), THERE_AND_BACK, A_MEDIUM_CAR);

        assertThat(names(plan)).containsExactly("Home", "Office", "Shop", "Home");
        // Still held against the shortest order, which it is.
        assertThat(plan.shortestOrder()).isPresent();
    }

    /**
     * One way, with the Shop on a hill 200 m above Home and the Office. Home, Office, Shop is the
     * shortest at 3 + 2 = 5 km and ends at the top. Home, Shop, Office is 2.5 + 3 = 5.5 km and
     * ends at the bottom, having come down at 6.7%, too steep to give back all the climb cost.
     */
    private static final TravelMatrixProvider A_SHOP_ON_A_HILL = places -> new TravelMatrix(
            new double[][] {
                {0, 3, 2.5},
                {3, 0, 2},
                {2.5, 3, 0},
            },
            new double[][] {
                {0, 180, 150},
                {180, 0, 120},
                {150, 180, 0},
            },
            new ClimbAndDescent[][] {
                {ClimbAndDescent.LEVEL, ClimbAndDescent.LEVEL, new ClimbAndDescent(200, 0)},
                {ClimbAndDescent.LEVEL, ClimbAndDescent.LEVEL, new ClimbAndDescent(200, 0)},
                {down(200, 2500), down(200, 3000), ClimbAndDescent.LEVEL},
            });

    @Test
    void doesNotEndAOneWayTripLowerToSaveFuel() {
        // Ending at the bottom takes 0.19 L less as driven, but only because the car is left 200 m
        // lower, and the next drive up takes that back. The way down was steep and gave back less.
        RoutePlan plan = forFuel(A_SHOP_ON_A_HILL, ONE_WAY, A_MEDIUM_CAR);

        assertThat(names(plan)).containsExactly("Home", "Office", "Shop");
    }

    /**
     * One way, with the Office on a hill 300 m above Home and the Shop. Home, Office, Shop is the
     * shortest at 3 + 1.5 = 4.5 km, and comes down to the Shop at 20%. Home, Shop, Office is
     * 2 + 3 = 5 km and ends at the top.
     */
    private static final TravelMatrixProvider AN_OFFICE_ON_A_STEEP_HILL = places -> new TravelMatrix(
            new double[][] {
                {0, 3, 2},
                {3, 0, 1.5},
                {2, 3, 0},
            },
            new double[][] {
                {0, 180, 120},
                {180, 0, 90},
                {120, 180, 0},
            },
            new ClimbAndDescent[][] {
                {ClimbAndDescent.LEVEL, THREE_HUNDRED_METRES_UP, ClimbAndDescent.LEVEL},
                {down(300, 3000), ClimbAndDescent.LEVEL, down(300, 1500)},
                {ClimbAndDescent.LEVEL, THREE_HUNDRED_METRES_UP, ClimbAndDescent.LEVEL},
            });

    @Test
    void keepsTheShortestOrderWhenTheOtherTakesMoreFuelAsDriven() {
        // Ending at the top keeps off the steep way down, and uses up 0.24 L less for good. But it
        // takes 0.15 L more as driven, since the car is left 300 m higher.
        RoutePlan plan = forFuel(AN_OFFICE_ON_A_STEEP_HILL, ONE_WAY, A_MEDIUM_CAR);

        assertThat(names(plan)).containsExactly("Home", "Office", "Shop");
    }

    @Test
    void choosesForDistanceWhenTheVehiclesWeightIsNotKnown() {
        RoutePlan plan = forFuel(aHillWithTheWayDownToHome(5), THERE_AND_BACK, Optional.of(new FuelUse(7.5)));

        // The fuel goes with the distance, so the shortest order takes the least.
        assertThat(names(plan)).containsExactly("Home", "Office", "Shop", "Home");
        assertThat(plan.shortestOrder()).isEmpty();
    }

    @Test
    void choosesForDistanceWhenNeitherTheSpeedsNorTheHillsAreKnown() {
        RoutePlan plan = forFuel(ROADS_WITH_TIMES, THERE_AND_BACK, A_MEDIUM_CAR);

        assertThat(plan.totalDistanceKm()).isEqualTo(35);
        assertThat(plan.shortestOrder()).isEmpty();
    }

    @Test
    void refusesToChooseForFuelWithoutAVehicle() {
        assertThatThrownBy(() -> forFuel(aHillWithTheWayDownToHome(5), THERE_AND_BACK, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vehicle");
    }

    @Test
    void leavesAKeptStopAtItsTurnWhenChoosingForFuel() {
        RoutePlan plan = new RoutePlanningService(aHillWithTheWayDownToHome(5), new BruteForceRouteSolver())
                .plan(HOME, OFFICE_AND_SHOP, Set.of(0), THERE_AND_BACK, ChosenFor.FUEL, A_MEDIUM_CAR);

        assertThat(names(plan)).containsExactly("Home", "Office", "Shop", "Home");
        assertThat(plan.ordersChecked()).isEqualTo(1);
    }

    @Test
    void choosesForDistanceWhenAVehicleIsSentWithoutAskingForFuel() {
        RoutePlan plan = new RoutePlanningService(aHillWithTheWayDownToHome(5), new BruteForceRouteSolver())
                .plan(HOME, OFFICE_AND_SHOP, NONE_KEPT, THERE_AND_BACK, ChosenFor.DISTANCE, A_MEDIUM_CAR);

        assertThat(names(plan)).containsExactly("Home", "Office", "Shop", "Home");
        assertThat(plan.shortestOrder()).isEmpty();
    }

    // The least fuel on roads whose speeds are known.

    /**
     * The roads with times, where one way round keeps to the open road and the other to town:
     * Home, Office, Shop and back is 36 km of open road, and Home, Shop, Office and back, the
     * shortest, is 35 km in town.
     */
    private static final TravelMatrixProvider TOWN_ONE_WAY_ROUND = places -> ROADS_WITH_TIMES.matrixFor(places)
            .withKmInTown(new double[][] {
                {0, 0, 20},
                {10, 0, 0},
                {0, 5, 0},
            });

    /** A car that takes 9 L/100 km in town and 6 on the open road, its weight not known. */
    private static final Optional<FuelUse> TOWN_AND_OPEN_ROAD = Optional.of(new FuelUse(7.5, 9.0, 6.0, OptionalDouble.empty()));

    @Test
    void goesFurtherToKeepToTheOpenRoad() {
        // 36 km at 6 L/100 km is 2.16 L. The shortest, 35 km at 9, is 3.15 L.
        RoutePlan plan = forFuel(TOWN_ONE_WAY_ROUND, THERE_AND_BACK, TOWN_AND_OPEN_ROAD);

        assertThat(names(plan)).containsExactly("Home", "Office", "Shop", "Home");
        assertThat(plan.totalDistanceKm()).isEqualTo(36);
    }

    @Test
    void givesEachLegAndTheShortestOrderTheirKilometresInTown() {
        RoutePlan plan = forFuel(TOWN_ONE_WAY_ROUND, THERE_AND_BACK, TOWN_AND_OPEN_ROAD);

        assertThat(plan.legs()).extracting(RoutePlan.Leg::kmInTown)
                .containsExactly(OptionalDouble.of(0), OptionalDouble.of(0), OptionalDouble.of(0));
        assertThat(plan.shortestOrder().orElseThrow().kmInTown()).hasValue(35);
    }

    // The other of the two choices, beside the one asked for.

    /**
     * The motorway to the Shop further up, all of it open road like the rest. Going there first is
     * 27 km in 1500 seconds, and the shortest order 26 km in 1800. The kilometre more takes more
     * fuel, so the fastest order is not the one taking the least.
     */
    private static TravelMatrixProvider openRoadToTheShopTaking(double seconds) {
        return places -> aMotorwayToTheShopTaking(seconds).matrixFor(places).withKmInTown(new double[3][3]);
    }

    private static RoutePlan onTheOpenRoad(ChosenFor chosenFor, Optional<FuelUse> vehicle) {
        return new RoutePlanningService(openRoadToTheShopTaking(600), new BruteForceRouteSolver())
                .plan(HOME, OFFICE_AND_SHOP, NONE_KEPT, THERE_AND_BACK, chosenFor, vehicle);
    }

    @Test
    void givesTheFastestOrderBesideTheLeastFuel() {
        RoutePlan plan = onTheOpenRoad(ChosenFor.FUEL, TOWN_AND_OPEN_ROAD);

        assertThat(names(plan)).containsExactly("Home", "Office", "Shop", "Home");
        assertThat(plan.otherChoice()).contains(new RoutePlan.OtherChoice(
                ChosenFor.TIME, 27, OptionalDouble.of(1500), Optional.empty(), OptionalDouble.of(0)));
    }

    @Test
    void givesTheLeastFuelBesideTheFastest() {
        RoutePlan plan = onTheOpenRoad(ChosenFor.TIME, TOWN_AND_OPEN_ROAD);

        assertThat(names(plan)).containsExactly("Home", "Shop", "Office", "Home");
        assertThat(plan.otherChoice()).contains(new RoutePlan.OtherChoice(
                ChosenFor.FUEL, 26, OptionalDouble.of(1800), Optional.empty(), OptionalDouble.of(0)));
    }

    @Test
    void holdsTheFastestBesideTheLeastFuelToItsMinute() {
        // With 870 seconds to the Shop the motorway saves half a minute, so the fastest is the
        // shortest order, as it would be if asked for.
        RoutePlan plan = new RoutePlanningService(openRoadToTheShopTaking(870), new BruteForceRouteSolver())
                .plan(HOME, OFFICE_AND_SHOP, NONE_KEPT, THERE_AND_BACK, ChosenFor.FUEL, TOWN_AND_OPEN_ROAD);

        assertThat(plan.otherChoice()).contains(new RoutePlan.OtherChoice(
                ChosenFor.TIME, 26, OptionalDouble.of(1800), Optional.empty(), OptionalDouble.of(0)));
    }

    @Test
    void givesThePlansOwnOrderAsTheOtherChoiceWhenBothComeToIt() {
        // Keeping to the open road is the least fuel and the quickest: 36 km in 300 seconds.
        RoutePlan plan = forFuel(TOWN_ONE_WAY_ROUND, THERE_AND_BACK, TOWN_AND_OPEN_ROAD);

        assertThat(plan.otherChoice()).contains(new RoutePlan.OtherChoice(
                ChosenFor.TIME, 36, OptionalDouble.of(300), Optional.empty(), OptionalDouble.of(0)));
    }

    @Test
    void leavesAKeptStopAtItsTurnForTheOtherChoiceToo() {
        // With the Office kept first there is one order, so the fastest is the same 26 km.
        RoutePlan plan = new RoutePlanningService(openRoadToTheShopTaking(600), new BruteForceRouteSolver())
                .plan(HOME, OFFICE_AND_SHOP, Set.of(0), THERE_AND_BACK, ChosenFor.FUEL, TOWN_AND_OPEN_ROAD);

        assertThat(plan.otherChoice().orElseThrow().distanceKm()).isEqualTo(26);
    }

    @Test
    void givesNoLeastFuelBesideTheFastestWithoutAVehicle() {
        assertThat(onTheOpenRoad(ChosenFor.TIME, Optional.empty()).otherChoice()).isEmpty();
    }

    @Test
    void givesNoOtherChoiceBesideTheShortestOrder() {
        assertThat(onTheOpenRoad(ChosenFor.DISTANCE, TOWN_AND_OPEN_ROAD).otherChoice()).isEmpty();
    }

    @Test
    void givesNoOtherChoiceOnStraightLines() {
        // A straight line says neither how long nor how fast, so neither choice can be made.
        RoutePlan plan = service.plan(SKY_TOWER, STOPS, NONE_KEPT, THERE_AND_BACK, ChosenFor.FUEL, A_MEDIUM_CAR);

        assertThat(plan.otherChoice()).isEmpty();
    }
}
