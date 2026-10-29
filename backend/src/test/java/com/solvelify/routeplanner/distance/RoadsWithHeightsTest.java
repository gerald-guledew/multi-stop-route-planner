package com.solvelify.routeplanner.distance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.dem.ElevationProvider;
import com.solvelify.routeplanner.planning.ClimbAndDescent;
import com.solvelify.routeplanner.planning.Drive;
import com.solvelify.routeplanner.planning.Location;
import com.solvelify.routeplanner.planning.TravelMatrix;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How much a drive climbs, on a map drawn by hand with a hill made up to go with it.
 *
 * <p>The real heights of New Zealand are hundreds of megabytes and not in git. This map is five
 * roads in {@code src/test/resources/maps/one-hill.osm}, and the ground is a few lines below, so
 * the test needs nothing fetched and runs everywhere. The engine is the one the application
 * builds, with the same settings. Only the map and the source of the heights are stand-ins.
 *
 * <p>The figures are known because the ground was drawn to give them: a hill that is 80 m up
 * its west side and 50 m down its east side, and further east a rise of 15 m and a bump of 6 m.
 *
 * <p>This is the whole way from a map to a figure. The rule that does the counting has tests of
 * its own in {@code HeightProfileTest}, and what it makes of real roads is in
 * {@code RealHeightsTest}.
 */
class RoadsWithHeightsTest {

    private static final Path MAP = Path.of("src/test/resources/maps/one-hill.osm");

    /**
     * The height of the ground by longitude, west to east. Between two of these it slopes
     * evenly, and beyond either end it is level.
     */
    private static final double[][] GROUND = {
        {174.70, 0},
        {174.71, 80},
        {174.72, 30},
        {174.76, 30},
        {174.77, 45},
        {174.78, 30},
        {174.80, 30},
        {174.81, 36},
        {174.82, 30},
    };

    private static final ElevationProvider ONE_HILL = new ElevationProvider() {
        @Override
        public double getEle(double latitude, double longitude) {
            if (longitude <= GROUND[0][0]) {
                return GROUND[0][1];
            }
            for (int next = 1; next < GROUND.length; next++) {
                double[] west = GROUND[next - 1];
                double[] east = GROUND[next];
                if (longitude <= east[0]) {
                    double along = (longitude - west[0]) / (east[0] - west[0]);
                    return west[1] + along * (east[1] - west[1]);
                }
            }
            return GROUND[GROUND.length - 1][1];
        }

        @Override
        public boolean canInterpolate() {
            return true;
        }

        @Override
        public void release() {
        }
    };

    // Summit Road has a point at each end and one at the top of the hill.
    private static final Location SUMMIT_ROAD_WEST = new Location("Summit Road, west end", -36.9000, 174.7000);
    private static final Location SUMMIT_ROAD_EAST = new Location("Summit Road, east end", -36.9100, 174.7200);

    // Long Road crosses the same hill and has a point at each end only.
    private static final Location LONG_ROAD_WEST = new Location("Long Road, west end", -36.9300, 174.7000);
    private static final Location LONG_ROAD_EAST = new Location("Long Road, east end", -36.9400, 174.7200);

    // Level Road runs along the east of the hill, 30 m up from end to end.
    private static final Location LEVEL_ROAD_NORTH = new Location("Level Road, north end", -36.9000, 174.7400);
    private static final Location LEVEL_ROAD_SOUTH = new Location("Level Road, south end", -36.9100, 174.7400);

    // Rise Road starts and ends at 30 m, with a point 15 m higher in the middle.
    private static final Location RISE_ROAD_WEST = new Location("Rise Road, west end", -36.9000, 174.7600);
    private static final Location RISE_ROAD_EAST = new Location("Rise Road, east end", -36.9100, 174.7800);

    // Bump Road is the same, with its point only 6 m higher.
    private static final Location BUMP_ROAD_WEST = new Location("Bump Road, west end", -36.9000, 174.8000);
    private static final Location BUMP_ROAD_EAST = new Location("Bump Road, east end", -36.9100, 174.8200);

    @TempDir
    static Path folder;

    private static GraphHopper engineWithHeights;
    private static GraphHopper flatEngine;
    private static GraphHopperTravelMatrixProvider withHeights;
    private static GraphHopperTravelMatrixProvider flat;

    @BeforeAll
    static void prepareBothEngines() {
        engineWithHeights = prepared("with-heights", ONE_HILL);
        flatEngine = prepared("flat", ElevationProvider.NOOP);

        RoutingProperties properties = new RoutingProperties(
                "graphhopper", MAP.toString(), folder.toString(), "car",
                new RoutingProperties.Heights(false, null, null));
        withHeights = new GraphHopperTravelMatrixProvider(engineWithHeights, properties);
        flat = new GraphHopperTravelMatrixProvider(flatEngine, properties);
    }

    private static GraphHopper prepared(String graphFolder, ElevationProvider heights) {
        GraphHopper engine = GraphHopperConfiguration.engineFor(
                MAP, folder.resolve(graphFolder).toString(), "car", heights);
        // Left alone, the engine sets aside any group of roads too small to be worth driving to,
        // which is right for a country and would be every road on this map.
        engine.setMinNetworkSize(0);
        engine.importOrLoad();
        return engine;
    }

    @AfterAll
    static void releaseBothEngines() {
        if (engineWithHeights != null) {
            engineWithHeights.close();
        }
        if (flatEngine != null) {
            flatEngine.close();
        }
    }

    @Test
    void countsTheWholeHillAndNotOnlyTheDifferenceBetweenTheEnds() {
        TravelMatrix matrix = withHeights.matrixFor(List.of(SUMMIT_ROAD_WEST, SUMMIT_ROAD_EAST));

        // The far end is 30 m higher than the near one. The drive climbs 80 m to get there.
        // A little under 80 is counted, because the top of this hill comes to a point, which
        // the top of no road does, and evening the heights out rounds it.
        ClimbAndDescent overTheHill = matrix.climbAndDescent(0, 1).orElseThrow();

        assertThat(overTheHill.climbMetres()).isCloseTo(80, within(3.0));
        assertThat(overTheHill.descentMetres()).isCloseTo(50, within(3.0));
        assertThat(overTheHill.climbMetres() - overTheHill.descentMetres()).isCloseTo(30, within(0.01));
    }

    @Test
    void drivesOneWayAsTheTableDoesOnRoadsWithHeights() {
        TravelMatrix matrix = withHeights.matrixFor(List.of(SUMMIT_ROAD_WEST, SUMMIT_ROAD_EAST));

        Drive drive = withHeights.driveBetween(SUMMIT_ROAD_WEST, SUMMIT_ROAD_EAST);

        // Off the map, as the table measures it, and not along the slope.
        assertThat(drive.distanceKm()).isEqualTo(matrix.distanceKm(0, 1));
        assertThat(drive.durationSeconds()).isEqualTo(matrix.durationSeconds(0, 1));
    }

    @Test
    void climbsOnTheWayBackWhatItCameDownOnTheWayOut() {
        TravelMatrix matrix = withHeights.matrixFor(List.of(SUMMIT_ROAD_WEST, SUMMIT_ROAD_EAST));

        ClimbAndDescent overTheHill = matrix.climbAndDescent(0, 1).orElseThrow();
        ClimbAndDescent backOverTheHill = matrix.climbAndDescent(1, 0).orElseThrow();

        assertThat(backOverTheHill.climbMetres()).isCloseTo(overTheHill.descentMetres(), within(0.01));
        assertThat(backOverTheHill.descentMetres()).isCloseTo(overTheHill.climbMetres(), within(0.01));
    }

    @Test
    void findsAHillThatNoPointOfTheRoadMarks() {
        TravelMatrix matrix = withHeights.matrixFor(List.of(LONG_ROAD_WEST, LONG_ROAD_EAST));

        ClimbAndDescent overTheHill = matrix.climbAndDescent(0, 1).orElseThrow();

        // The map gives this road two points, one at each end, and the hill is between them.
        // Heights are taken along it every 60 m, so the very top can fall between two of them
        // and a metre or two of it be missed, on top of what the evening out takes.
        assertThat(overTheHill.climbMetres()).isCloseTo(80, within(4.0));
        assertThat(overTheHill.descentMetres()).isCloseTo(50, within(4.0));
    }

    @Test
    void countsARiseOfFifteenMetres() {
        TravelMatrix matrix = withHeights.matrixFor(List.of(RISE_ROAD_WEST, RISE_ROAD_EAST));

        // Both ends are at 30 m, so a road taken as one even slope would be level. The engine
        // keeps a point when dropping it would move the road by more than a metre, and this
        // one is 15.
        ClimbAndDescent overTheRise = matrix.climbAndDescent(0, 1).orElseThrow();

        assertThat(overTheRise.climbMetres()).isCloseTo(15, within(1.0));
        assertThat(overTheRise.descentMetres()).isCloseTo(15, within(1.0));
    }

    @Test
    void passesOverABumpOfSixMetres() {
        TravelMatrix matrix = withHeights.matrixFor(List.of(BUMP_ROAD_WEST, BUMP_ROAD_EAST));

        // The engine itself reports 6 m up and 6 m down here. On the real map most of what it
        // reports is of this size and is not there on the road, so nothing under 10 m counts.
        assertThat(matrix.climbAndDescent(0, 1)).contains(ClimbAndDescent.LEVEL);
    }

    @Test
    void saysALevelRoadIsLevel() {
        TravelMatrix matrix = withHeights.matrixFor(List.of(LEVEL_ROAD_NORTH, LEVEL_ROAD_SOUTH));

        assertThat(matrix.climbAndDescent(0, 1)).contains(ClimbAndDescent.LEVEL);
        assertThat(matrix.climbAndDescent(1, 0)).contains(ClimbAndDescent.LEVEL);
    }

    @Test
    void climbsNothingToStayWhereYouAre() {
        TravelMatrix matrix = withHeights.matrixFor(List.of(SUMMIT_ROAD_WEST, SUMMIT_ROAD_EAST));

        assertThat(matrix.climbAndDescent(0, 0)).contains(ClimbAndDescent.LEVEL);
        assertThat(matrix.climbAndDescent(1, 1)).contains(ClimbAndDescent.LEVEL);
    }

    @Test
    void saysNothingAboutClimbWhenTheRoadsWereReadWithoutHeights() {
        TravelMatrix matrix = flat.matrixFor(List.of(SUMMIT_ROAD_WEST, SUMMIT_ROAD_EAST));

        // The engine itself answers nought here. Nought would read as a level road.
        assertThat(matrix.climbAndDescent(0, 1)).isEmpty();
        assertThat(matrix.climbAndDescent(0, 0)).isEmpty();
    }

    @Test
    void stillGivesDistanceAndTimeWithoutHeights() {
        TravelMatrix matrix = flat.matrixFor(List.of(SUMMIT_ROAD_WEST, SUMMIT_ROAD_EAST));

        assertThat(matrix.distanceKm(0, 1)).isCloseTo(2.097, within(0.005));
        assertThat(matrix.durationSeconds(0, 1)).isPresent();
    }

    @Test
    void measuresARoadOnTheMapWhetherItHasHeightsOrNot() {
        double flatKm = flat.matrixFor(List.of(SUMMIT_ROAD_WEST, SUMMIT_ROAD_EAST)).distanceKm(0, 1);
        double withHeightsKm = withHeights.matrixFor(List.of(SUMMIT_ROAD_WEST, SUMMIT_ROAD_EAST)).distanceKm(0, 1);

        // The engine measures a road with heights along its slope, which makes this one 4.2 m
        // longer. On the real map the slope it measures is mostly the wobble of the grid, so
        // the distance is taken off the map either way and does not move.
        assertThat(withHeightsKm * 1000).isCloseTo(flatKm * 1000, within(0.01));
    }

    @Test
    void takesAsLongToDriveAsTheEngineSays() {
        TravelMatrix matrix = withHeights.matrixFor(List.of(SUMMIT_ROAD_WEST, SUMMIT_ROAD_EAST));

        // 2.1 km of residential street at 30 km/h.
        assertThat(matrix.durationSeconds(0, 1).orElseThrow()).isCloseTo(252, within(1.0));
    }
}
