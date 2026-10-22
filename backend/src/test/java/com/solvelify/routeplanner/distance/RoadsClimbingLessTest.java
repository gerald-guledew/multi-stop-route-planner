package com.solvelify.routeplanner.distance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.util.DistanceCalcEarth;
import com.solvelify.routeplanner.planning.ClimbAndDescent;
import com.solvelify.routeplanner.planning.GeoPoint;
import com.solvelify.routeplanner.planning.Location;
import com.solvelify.routeplanner.planning.Roads;
import com.solvelify.routeplanner.planning.TravelMatrix;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The roads that climb less, on a map drawn by hand: a hill, a road over it, and a longer road
 * round it.
 *
 * <p>The map is {@code src/test/resources/maps/round-a-hill.osm}, and the hill is a few lines
 * below. Hill Road is 3.03 km and climbs 80 m. Round Road is 3.51 km and level. Counting a metre
 * climbed as 10 m of road, as the planner ships, Hill Road weighs as if it were 3.83 km, so the
 * way round comes out ahead. Counting it as 2 m, Hill Road weighs as 3.19 km and still wins.
 */
class RoadsClimbingLessTest {

    private static final Path MAP = Path.of("src/test/resources/maps/round-a-hill.osm");

    private static final double SUMMIT_LATITUDE = -36.9000;
    private static final double SUMMIT_LONGITUDE = 174.7000;
    private static final double SUMMIT_METRES = 80;
    private static final double FOOT_OF_THE_HILL_METRES = 600;

    /** A round hill, even on every side, on level ground at nought. */
    private static final ElevationProvider ONE_HILL = new ElevationProvider() {
        @Override
        public double getEle(double latitude, double longitude) {
            double fromTheTop = DistanceCalcEarth.DIST_EARTH.calcDist(latitude, longitude, SUMMIT_LATITUDE, SUMMIT_LONGITUDE);
            return SUMMIT_METRES * Math.max(0, 1 - fromTheTop / FOOT_OF_THE_HILL_METRES);
        }

        @Override
        public boolean canInterpolate() {
            return true;
        }

        @Override
        public void release() {
        }
    };

    private static final Location WEST_END = new Location("West end", -36.9000, 174.6830);
    private static final Location EAST_END = new Location("East end", -36.9000, 174.7170);
    private static final Location ROUND_ROAD_BEND = new Location("Round Road, west bend", -36.9060, 174.6920);

    @TempDir
    static Path folder;

    private static GraphHopper asShipped;
    private static GraphHopper countingTwoMetres;
    private static GraphHopper flat;

    @BeforeAll
    static void prepareTheEngines() {
        asShipped = prepared("as-shipped", ONE_HILL, 10);
        countingTwoMetres = prepared("two-metres", ONE_HILL, 2);
        flat = prepared("flat", ElevationProvider.NOOP, 10);
    }

    private static GraphHopper prepared(String graphFolder, ElevationProvider heights, double roadMetresPerMetreClimbed) {
        GraphHopper engine = GraphHopperConfiguration.engineFor(
                MAP, folder.resolve(graphFolder).toString(), "car", heights, roadMetresPerMetreClimbed);
        // Left alone, the engine sets aside any group of roads too small to be worth driving to,
        // which is right for a country and would be every road on this map.
        engine.setMinNetworkSize(0);
        engine.importOrLoad();
        return engine;
    }

    @AfterAll
    static void releaseTheEngines() {
        for (GraphHopper engine : new GraphHopper[] {asShipped, countingTwoMetres, flat}) {
            if (engine != null) {
                engine.close();
            }
        }
    }

    private static GraphHopperTravelMatrixProvider providerFor(GraphHopper engine) {
        return new GraphHopperTravelMatrixProvider(engine, new RoutingProperties(
                "graphhopper", MAP.toString(), folder.toString(), "car",
                new RoutingProperties.Heights(false, null, null)));
    }

    private static TravelMatrix acrossTheHill(GraphHopper engine, Roads roads) {
        return providerFor(engine).matrixFor(List.of(WEST_END, EAST_END), roads);
    }

    @Test
    void goesOverTheHillOnTheUsualRoads() {
        TravelMatrix usual = acrossTheHill(asShipped, Roads.USUAL);

        assertThat(usual.distanceKm(0, 1)).isCloseTo(3.03, within(0.01));
        assertThat(usual.climbAndDescent(0, 1).orElseThrow().climbMetres()).isCloseTo(80, within(5.0));
    }

    @Test
    void goesRoundTheHillOnTheRoadsThatClimbLess() {
        TravelMatrix climbingLess = acrossTheHill(asShipped, Roads.CLIMBING_LESS);

        assertThat(climbingLess.distanceKm(0, 1)).isCloseTo(3.51, within(0.01));
        assertThat(climbingLess.climbAndDescent(0, 1)).contains(ClimbAndDescent.LEVEL);
    }

    @Test
    void goesRoundTheHillOnTheWayBackToo() {
        TravelMatrix climbingLess = acrossTheHill(asShipped, Roads.CLIMBING_LESS);

        assertThat(climbingLess.distanceKm(1, 0)).isCloseTo(3.51, within(0.01));
    }

    @Test
    void takesAsLongAsTheRoadRoundTheHillTakes() {
        TravelMatrix climbingLess = acrossTheHill(asShipped, Roads.CLIMBING_LESS);

        // 3.51 km of residential street at 30 km/h. The climb weighs on which road is taken,
        // and not on how long it takes.
        assertThat(climbingLess.durationSeconds(0, 1).orElseThrow()).isCloseTo(421, within(2.0));
    }

    @Test
    void drawsTheRoadRoundTheHill() {
        GraphHopperTravelMatrixProvider provider = providerFor(asShipped);

        List<GeoPoint> usual = provider.directionsBetween(WEST_END, EAST_END, Roads.USUAL).path();
        List<GeoPoint> climbingLess = provider.directionsBetween(WEST_END, EAST_END, Roads.CLIMBING_LESS).path();

        // Round Road runs 0.006 degrees south of Hill Road.
        assertThat(usual).allSatisfy(point -> assertThat(point.latitude()).isCloseTo(-36.9000, within(0.0001)));
        assertThat(climbingLess).anySatisfy(point -> assertThat(point.latitude()).isCloseTo(-36.9060, within(0.0001)));
    }

    @Test
    void takesTheUsualRoadWhereThereIsNoClimbToSave() {
        GraphHopperTravelMatrixProvider provider = providerFor(asShipped);
        List<Location> alongTheLevel = List.of(WEST_END, ROUND_ROAD_BEND);

        TravelMatrix usual = provider.matrixFor(alongTheLevel, Roads.USUAL);
        TravelMatrix climbingLess = provider.matrixFor(alongTheLevel, Roads.CLIMBING_LESS);

        assertThat(climbingLess.distanceKm(0, 1)).isEqualTo(usual.distanceKm(0, 1));
        assertThat(climbingLess.durationSeconds(0, 1)).isEqualTo(usual.durationSeconds(0, 1));
    }

    @Test
    void countsAMetreClimbedAsManyMetresOfRoadAsItIsSetTo() {
        // At 2 m of road each, the 80 m climbed count as 160 m. The way round is 480 m longer.
        TravelMatrix climbingLess = acrossTheHill(countingTwoMetres, Roads.CLIMBING_LESS);

        assertThat(climbingLess.distanceKm(0, 1)).isCloseTo(3.03, within(0.01));
    }

    @Test
    void goesOverTheHillWhenTheRoadsHaveNoHeights() {
        TravelMatrix climbingLess = acrossTheHill(flat, Roads.CLIMBING_LESS);

        assertThat(climbingLess.distanceKm(0, 1)).isCloseTo(3.03, within(0.01));
        assertThat(climbingLess.climbAndDescent(0, 1)).isEmpty();
        assertThat(flat.getProfile(GraphHopperConfiguration.climbingLess("car"))).isNull();
    }

    @Test
    void refusesAGraphPreparedWithAnotherFigure() {
        // Each road's weight is prepared ahead with the figure. Read with another, the roads
        // would be chosen with the old one and nothing would say so.
        GraphHopper countingFourMetres = GraphHopperConfiguration.engineFor(
                MAP, folder.resolve("as-shipped").toString(), "car", ONE_HILL, 4);

        assertThatThrownBy(countingFourMetres::importOrLoad)
                .hasMessageContaining("Profiles do not match")
                .hasMessageContaining(GraphHopperConfiguration.climbingLess("car"));
    }
}
