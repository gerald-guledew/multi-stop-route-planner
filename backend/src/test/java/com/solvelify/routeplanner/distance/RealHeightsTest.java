package com.solvelify.routeplanner.distance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.dem.ElevationProvider;
import com.solvelify.routeplanner.planning.BruteForceRouteSolver;
import com.solvelify.routeplanner.planning.ChosenFor;
import com.solvelify.routeplanner.planning.ClimbAndDescent;
import com.solvelify.routeplanner.planning.FuelEstimate;
import com.solvelify.routeplanner.planning.FuelUse;
import com.solvelify.routeplanner.planning.Location;
import com.solvelify.routeplanner.planning.RoutePlan;
import com.solvelify.routeplanner.planning.RoutePlanningService;
import com.solvelify.routeplanner.planning.TravelMatrix;
import com.solvelify.routeplanner.planning.Turn;
import com.solvelify.routeplanner.vehicle.VehicleSize;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * What the planner says real roads climb, against what is known about them.
 *
 * <p>Needs the graph prepared with heights, which exists only where somebody has started the
 * application once with {@code routeplanner.routing.heights.enabled=true}. Anywhere else this
 * test skips itself. It never prepares that graph and never fetches a height file: the source
 * of heights it hands the engine refuses to answer.
 *
 * <p>The figures are loose on purpose. The heights come from a grid of the ground, and these
 * tests are here to catch a figure that has stopped making sense, not one that moved a metre.
 * What was measured when the rule was chosen is in the comments.
 */
class RealHeightsTest {

    private static final Path OSM_FILE = Path.of("data/new-zealand-latest.osm.pbf");
    private static final Path GRAPH_WITH_HEIGHTS = Path.of("data/graph-cache-heights");

    private static final ElevationProvider NEVER_ASKED = new ElevationProvider() {
        @Override
        public double getEle(double latitude, double longitude) {
            throw new IllegalStateException("The graph with heights was to be read, not prepared");
        }

        @Override
        public boolean canInterpolate() {
            return true;
        }

        @Override
        public void release() {
        }
    };

    private static GraphHopper graphHopper;
    private static GraphHopperTravelMatrixProvider provider;

    @BeforeAll
    static void readTheGraphWithHeights() {
        assumeTrue(Files.exists(GRAPH_WITH_HEIGHTS.resolve("properties")),
                "No graph with heights at " + GRAPH_WITH_HEIGHTS.toAbsolutePath() + ", skipping the tests on real heights");

        graphHopper = GraphHopperConfiguration.engineFor(OSM_FILE, GRAPH_WITH_HEIGHTS.toString(), "car", NEVER_ASKED);
        assertThat(graphHopper.load()).as("the graph with heights could be read").isTrue();

        provider = new GraphHopperTravelMatrixProvider(
                graphHopper,
                new RoutingProperties(
                        "graphhopper", OSM_FILE.toString(), GRAPH_WITH_HEIGHTS.toString(), "car",
                        new RoutingProperties.Heights(true, "data/heights", GRAPH_WITH_HEIGHTS.toString())));
    }

    @AfterAll
    static void releaseTheGraph() {
        if (graphHopper != null) {
            graphHopper.close();
        }
    }

    // Roads that only climb. Whatever comes out as descent is error, or a dip too small to see
    // on a map.

    @Test
    void climbsTheRemutakaHillByLittleMoreThanItsHeight() {
        // From the edge of Featherston, about 45 m up, to the summit, published as 555 m.
        ClimbAndDescent toTheSummit = between(
                new Location("Featherston", -41.11705, 175.32434),
                new Location("Remutaka summit", -41.11532, 175.23369));

        // Measured: 523 m up and 10 m down, where the engine's own totals were 771 and 258.
        assertThat(toTheSummit.climbMetres()).isBetween(505.0, 550.0);
        assertThat(toTheSummit.descentMetres()).isLessThan(25);
        assertThat(toTheSummit.climbMetres() - toTheSummit.descentMetres()).isCloseTo(510, within(15.0));
    }

    @Test
    void climbsTheCrownRangeByLittleMoreThanItsHeight() {
        // From the river at the foot of the zigzag, about 375 m up, to the summit, published
        // as 1,076 m.
        ClimbAndDescent toTheSummit = between(
                new Location("Foot of the zigzag", -44.97730, 168.85141),
                new Location("Crown Range summit", -44.99085, 168.93991));

        // Measured: 722 m up and 13 m down, where the engine's own totals were 809 and 100.
        assertThat(toTheSummit.climbMetres()).isBetween(700.0, 745.0);
        assertThat(toTheSummit.descentMetres()).isLessThan(30);
        assertThat(toTheSummit.climbMetres() - toTheSummit.descentMetres()).isCloseTo(701, within(15.0));
    }

    @Test
    void comesBackDownWhatItClimbed() {
        TravelMatrix matrix = provider.matrixFor(List.of(
                new Location("Featherston", -41.11705, 175.32434),
                new Location("Remutaka summit", -41.11532, 175.23369)));

        ClimbAndDescent up = matrix.climbAndDescent(0, 1).orElseThrow();
        ClimbAndDescent down = matrix.climbAndDescent(1, 0).orElseThrow();

        // The same road both ways, give or take where the two lanes part.
        assertThat(down.descentMetres()).isCloseTo(up.climbMetres(), within(5.0));
        assertThat(down.climbMetres()).isCloseTo(up.descentMetres(), within(5.0));
    }

    // The turns of a drive.

    @Test
    void signpostsTheWayFromTheSkyTowerToTheAirport() {
        List<Turn> turns = provider.directionsBetween(THE_ROUND.get(0), THE_ROUND.get(1)).turns();

        // Measured: off the motorway at the Gillies Avenue exit, by the signs for the airport.
        assertThat(turns).anySatisfy(turn -> {
            assertThat(turn.motorwayExit()).contains("Gillies Avenue");
            assertThat(turn.towards()).contains("Airport");
        });
        assertThat(turns.getLast().way()).isEqualTo(Turn.Way.ARRIVE);
    }

    @Test
    void addsUpTheTurnsToTheLegAlongTheSlopeOfItsRoads() {
        Location featherston = new Location("Featherston", -41.11705, 175.32434);
        Location summit = new Location("Remutaka summit", -41.11532, 175.23369);

        double legMetres = provider.matrixFor(List.of(featherston, summit)).distanceKm(0, 1) * 1000;
        double turnMetres = provider.directionsBetween(featherston, summit).turns().stream()
                .mapToDouble(Turn::metres)
                .sum();

        // The engine measures each stretch along its slope, and the leg is measured on the map.
        // Measured on one of the steepest roads there is: 10,521 m of turns against 10,426 m of
        // leg, 0.9% more.
        assertThat(turnMetres / legMetres).isBetween(1.0, 1.015);
    }

    // Roads that are level.

    @Test
    void findsNoClimbAcrossThePlains() {
        // Ngatea to Kopu, 19 km across the Hauraki Plains, never more than 8 m above the sea.
        ClimbAndDescent acrossThePlains = between(
                new Location("Ngatea", -37.2770, 175.4930),
                new Location("Kopu", -37.1890, 175.5650));

        // Measured: nothing up, 0.4 m down. The engine's own totals were 113 m up and 114 down.
        assertThat(acrossThePlains.climbMetres()).isLessThan(5);
        assertThat(acrossThePlains.descentMetres()).isLessThan(5);
    }

    @Test
    void climbsOnlyTheSlopeOfALongGentleRise() {
        // Rolleston to Rakaia, 35 km of State Highway 1 rising 52 m with no hill on it.
        ClimbAndDescent upThePlains = between(
                new Location("Rolleston", -43.5960, 172.3800),
                new Location("Rakaia", -43.7550, 172.0230));

        // Measured: 52 m up, nothing down. The engine's own totals were 288 m up and 236 down.
        assertThat(upThePlains.climbMetres()).isBetween(45.0, 62.0);
        assertThat(upThePlains.descentMetres()).isLessThan(5);
    }

    // What the heights do not know.

    @Test
    void doesNotSeeTheRiseOfAHighBridge() {
        // Over the Auckland Harbour Bridge, whose deck rises more than 40 m above the water.
        ClimbAndDescent overTheBridge = between(
                new Location("Fanshawe Street", -36.8440, 174.7560),
                new Location("Northcote", -36.8150, 174.7460));

        // The ground under a bridge is the water, so the engine draws a bridge as one even
        // slope from end to end. This is a known gap, held here so that it is not forgotten.
        // Measured: 4 m up.
        assertThat(overTheBridge.climbMetres()).isLessThan(15);
    }

    // The five stops of docs/api.md, in the order the planner puts them by road.

    private static final List<Location> THE_ROUND = List.of(
            new Location("Sky Tower", -36.8485, 174.7621),
            new Location("Airport", -37.0070, 174.7830),
            new Location("Sylvia Park", -36.9170, 174.8414),
            new Location("Takapuna", -36.7870, 174.7740),
            new Location("Devonport", -36.8330, 174.7955));

    @Test
    void climbsAsMuchAsItDescendsOnARoundTrip() {
        ClimbAndDescent round = aroundTheFiveStops();

        // Measured: 478 m each way. The engine's own totals came to 1,345 m.
        assertThat(round.climbMetres()).isBetween(430.0, 530.0);
        assertThat(round.descentMetres()).isCloseTo(round.climbMetres(), within(1.0));
    }

    @Test
    void costsARoundTripNextToNothingMoreForItsHills() {
        // A medium car at 7.5 L/100 km. Measured: 5.768 L on the level and 5.769 L with the
        // hills, where the legs ran from 3.5% less, down to the airport, to 5.5% more, up from
        // Devonport. The descents are gentle enough to give back nearly all the climbs cost.
        FuelUse mediumCar = new FuelUse(7.5, OptionalDouble.of(VehicleSize.MEDIUM.kilograms()));
        TravelMatrix matrix = provider.matrixFor(THE_ROUND);

        double level = 0;
        double withHills = 0;
        double[] eachLeg = new double[THE_ROUND.size()];
        for (int stop = 0; stop < THE_ROUND.size(); stop++) {
            int next = (stop + 1) % THE_ROUND.size();
            double onTheLevel = mediumCar.litresFor(matrix.distanceKm(stop, next));
            double counted = mediumCar.litresFor(matrix.distanceKm(stop, next), matrix.climbAndDescent(stop, next));
            level += onTheLevel;
            withHills += counted;
            eachLeg[stop] = counted / onTheLevel;
        }

        assertThat(withHills / level).isCloseTo(1, within(0.005));
        assertThat(eachLeg[0]).as("down to the airport").isBetween(0.94, 0.99);
        assertThat(eachLeg[4]).as("up from Devonport").isBetween(1.03, 1.08);
    }

    @Test
    void measuresTheTripAsLongAsItIsWithoutHeights() {
        TravelMatrix matrix = provider.matrixFor(THE_ROUND);

        double km = 0;
        double seconds = 0;
        for (int stop = 0; stop < THE_ROUND.size(); stop++) {
            int next = (stop + 1) % THE_ROUND.size();
            km += matrix.distanceKm(stop, next);
            seconds += matrix.durationSeconds(stop, next).orElseThrow();
        }

        // 76.91 km and 5,057 seconds on the graph without heights. The distance is measured on
        // the map either way. The time is the engine's, which measures along the slope, bumps
        // of the grid included, and comes out 6 seconds longer.
        assertThat(km).isCloseTo(76.91, within(0.01));
        assertThat(seconds).isCloseTo(5057, within(15.0));
    }

    // The order of the stops, chosen for driving time and for fuel. Both are in docs/api.md.

    @Test
    void findsAQuickerOrderForTheFiveStops() {
        RoutePlan plan = planner().plan(
                THE_ROUND.getFirst(), THE_ROUND.subList(1, THE_ROUND.size()), Set.of(), true, ChosenFor.TIME);

        // Measured: 77.33 km in 4,952 seconds, where the shortest order is 76.91 km in 5,063.
        RoutePlan.ShortestOrder shortest = plan.shortestOrder().orElseThrow();
        double saved = shortest.durationSeconds().orElseThrow() - plan.totalDurationSeconds().orElseThrow();
        assertThat(saved).isBetween(60.0, 180.0);
        assertThat(plan.totalDistanceKm() - shortest.distanceKm()).isBetween(0.1, 1.0);
        assertThat(plan.route()).extracting(Location::name)
                .containsExactly("Sky Tower", "Takapuna", "Devonport", "Sylvia Park", "Airport", "Sky Tower");
    }

    @Test
    void keepsToTheMotorwayForTheLeastFuelAroundTheFiveStops() {
        FuelUse aMediumCar = new FuelUse(7.5, OptionalDouble.of(VehicleSize.MEDIUM.kilograms()));

        RoutePlan plan = planner().plan(
                THE_ROUND.getFirst(), THE_ROUND.subList(1, THE_ROUND.size()), Set.of(), true, ChosenFor.FUEL,
                Optional.of(aMediumCar));
        FuelEstimate fuel = FuelEstimate.of(plan, aMediumCar);

        // Measured: the fastest order, 77.33 km for 6.11 L, where the shortest is 76.91 km for 6.19.
        // It keeps to the motorway, where a kilometre takes less than on town roads.
        double saved = fuel.shortestOrderLitres().orElseThrow() - fuel.totalLitres();
        assertThat(saved).isBetween(0.03, 0.2);
        assertThat(fuel.countsSpeed()).isTrue();
        assertThat(plan.route()).extracting(Location::name)
                .containsExactly("Sky Tower", "Takapuna", "Devonport", "Sylvia Park", "Airport", "Sky Tower");
    }

    @Test
    void setsEachChoiceBesideTheOtherAsPlanningForItGives() {
        Optional<FuelUse> aMediumCar = Optional.of(new FuelUse(7.5, OptionalDouble.of(VehicleSize.MEDIUM.kilograms())));
        List<Location> stops = THE_ROUND.subList(1, THE_ROUND.size());

        RoutePlan forFuel = planner().plan(THE_ROUND.getFirst(), stops, Set.of(), true, ChosenFor.FUEL, aMediumCar);
        RoutePlan forTime = planner().plan(THE_ROUND.getFirst(), stops, Set.of(), true, ChosenFor.TIME, aMediumCar);

        RoutePlan.OtherChoice fastest = forFuel.otherChoice().orElseThrow();
        assertThat(fastest.chosenFor()).isEqualTo(ChosenFor.TIME);
        assertThat(fastest.distanceKm()).isEqualTo(forTime.totalDistanceKm());
        assertThat(fastest.durationSeconds()).isEqualTo(forTime.totalDurationSeconds());
        RoutePlan.OtherChoice leastFuel = forTime.otherChoice().orElseThrow();
        assertThat(leastFuel.chosenFor()).isEqualTo(ChosenFor.FUEL);
        assertThat(leastFuel.distanceKm()).isEqualTo(forFuel.totalDistanceKm());
        assertThat(leastFuel.durationSeconds()).isEqualTo(forFuel.totalDurationSeconds());
    }

    private static RoutePlanningService planner() {
        return new RoutePlanningService(provider, new BruteForceRouteSolver());
    }

    private static ClimbAndDescent aroundTheFiveStops() {
        TravelMatrix matrix = provider.matrixFor(THE_ROUND);
        ClimbAndDescent round = ClimbAndDescent.LEVEL;
        for (int stop = 0; stop < THE_ROUND.size(); stop++) {
            round = round.plus(matrix.climbAndDescent(stop, (stop + 1) % THE_ROUND.size()).orElseThrow());
        }
        return round;
    }

    private static ClimbAndDescent between(Location from, Location to) {
        return provider.matrixFor(List.of(from, to)).climbAndDescent(0, 1).orElseThrow();
    }
}
