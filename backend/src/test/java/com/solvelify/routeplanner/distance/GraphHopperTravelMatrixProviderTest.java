package com.solvelify.routeplanner.distance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.util.GHUtility;
import com.solvelify.routeplanner.planning.Location;
import com.solvelify.routeplanner.planning.TravelMatrix;
import com.solvelify.routeplanner.planning.UnroutablePlaceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Routes on the real New Zealand map.
 *
 * <p>The map extract is 385 MB and deliberately not in git, so this test skips itself when the
 * file is absent. That keeps CI green on a clean machine without downloading anything, while the
 * test still runs in full on a laptop that has the data.
 *
 * <p>The first run builds a routing graph and takes minutes. Later runs load the prepared graph
 * from {@code data/graph-cache} in seconds.
 */
class GraphHopperTravelMatrixProviderTest {

    private static final Path OSM_FILE = Path.of("data/new-zealand-latest.osm.pbf");
    private static final Path GRAPH_CACHE = Path.of("data/graph-cache");

    private static final Location DEVONPORT = new Location("Devonport", -36.8330, 174.7955);
    private static final Location SYLVIA_PARK = new Location("Sylvia Park", -36.9170, 174.8414);
    private static final Location SKY_TOWER = new Location("Sky Tower", -36.8485, 174.7621);

    private static GraphHopper graphHopper;
    private static GraphHopperTravelMatrixProvider provider;

    @BeforeAll
    static void prepareRoutingEngine() {
        assumeTrue(Files.exists(OSM_FILE),
                "Map extract missing at " + OSM_FILE.toAbsolutePath() + ", skipping road distance tests");

        graphHopper = new GraphHopper();
        graphHopper.setOSMFile(OSM_FILE.toString());
        graphHopper.setGraphHopperLocation(GRAPH_CACHE.toString());
        graphHopper.setEncodedValuesString("car_access, car_average_speed, road_access");
        graphHopper.setProfiles(new Profile("car").setCustomModel(GHUtility.loadCustomModelFromJar("car.json")));
        graphHopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));
        graphHopper.importOrLoad();

        provider = new GraphHopperTravelMatrixProvider(
                graphHopper,
                new RoutingProperties("graphhopper", OSM_FILE.toString(), GRAPH_CACHE.toString(), "car"));
    }

    @AfterAll
    static void releaseRoutingEngine() {
        if (graphHopper != null) {
            graphHopper.close();
        }
    }

    @Test
    void takesTheLongWayRoundTheHarbour() {
        double straightLine = Haversine.distanceKm(DEVONPORT, SYLVIA_PARK);
        TravelMatrix matrix = provider.matrixFor(List.of(DEVONPORT, SYLVIA_PARK));

        double byRoad = matrix.distanceKm(0, 1);

        // Straight across the water is about 10 km. The drive goes round by the bridge.
        assertThat(straightLine).isLessThan(11);
        assertThat(byRoad).isGreaterThan(15).isLessThan(60);
    }

    @Test
    void neverFindsARoadShorterThanTheStraightLine() {
        List<Location> places = List.of(SKY_TOWER, DEVONPORT, SYLVIA_PARK);
        TravelMatrix matrix = provider.matrixFor(places);

        for (int from = 0; from < places.size(); from++) {
            for (int to = 0; to < places.size(); to++) {
                if (from != to) {
                    assertThat(matrix.distanceKm(from, to))
                            .as("road distance from %s to %s", places.get(from).name(), places.get(to).name())
                            .isGreaterThanOrEqualTo(
                                    Haversine.distanceKm(places.get(from), places.get(to)));
                }
            }
        }
    }

    @Test
    void measuresZeroAlongTheDiagonal() {
        TravelMatrix matrix = provider.matrixFor(List.of(SKY_TOWER, DEVONPORT, SYLVIA_PARK));

        for (int place = 0; place < matrix.size(); place++) {
            assertThat(matrix.distanceKm(place, place)).isZero();
        }
    }

    @Test
    void buildsOneRowAndColumnPerPlace() {
        assertThat(provider.matrixFor(List.of(SKY_TOWER, DEVONPORT, SYLVIA_PARK)).size()).isEqualTo(3);
    }

    @Test
    void namesThePlaceThatHasNoRoadNearIt() {
        // Middle of the airfield. Fine as a map pin, useless as a destination for a car.
        Location onTheRunway = new Location("Airport", -37.0082, 174.7850);

        assertThatThrownBy(() -> provider.matrixFor(List.of(SKY_TOWER, onTheRunway)))
                .isInstanceOf(UnroutablePlaceException.class)
                .hasMessageContaining("Airport")
                .extracting(exception -> ((UnroutablePlaceException) exception).placeName())
                .isEqualTo("Airport");
    }

    @Test
    void routesToTheTerminalForecourtInstead() {
        // Two hundred metres away, but on a street, so the car can get there.
        Location terminal = new Location("Airport terminal", -37.0070, 174.7830);

        double byRoad = provider.matrixFor(List.of(SKY_TOWER, terminal)).distanceKm(0, 1);

        assertThat(byRoad).isGreaterThan(15).isLessThan(30);
    }
}
