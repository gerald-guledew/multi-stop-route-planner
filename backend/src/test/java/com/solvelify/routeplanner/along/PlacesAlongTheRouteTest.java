package com.solvelify.routeplanner.along;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.solvelify.routeplanner.planning.Directions;
import com.solvelify.routeplanner.planning.Drive;
import com.solvelify.routeplanner.planning.GeoPoint;
import com.solvelify.routeplanner.planning.Location;
import com.solvelify.routeplanner.planning.TravelMatrix;
import com.solvelify.routeplanner.planning.TravelMatrixProvider;
import com.solvelify.routeplanner.planning.UnroutablePlaceException;
import com.solvelify.routeplanner.search.FoundPlace;
import com.solvelify.routeplanner.search.PlaceSearch;
import com.solvelify.routeplanner.search.ReferencePoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Plain Java with a search and roads made up for each test, so what a place adds is worked out
 * from figures the test states.
 */
class PlacesAlongTheRouteTest {

    private static final Location START = new Location("Start", -36.90, 174.70);
    private static final Location SHOPS = new Location("Shops", -36.90, 174.80);
    private static final Location WORK = new Location("Work", -36.80, 174.80);

    /** What the search hands over, in the order it ranks them. */
    private final List<FoundPlace> found = new ArrayList<>();
    private List<ReferencePoint> roadLookedAlong;
    private int placesAskedFor;

    private final PlaceSearch search = new PlaceSearch() {
        @Override
        public List<FoundPlace> search(String typed, ReferencePoint near, int limit) {
            throw new AssertionError("looks along the road, not from a point");
        }

        @Override
        public List<FoundPlace> searchAlong(String typed, List<ReferencePoint> road, int limit) {
            roadLookedAlong = road;
            placesAskedFor = limit;
            return found;
        }
    };

    /** Drives by the names at their ends, as "From>To". */
    private final Map<String, Drive> drives = new HashMap<>();
    private final Map<String, Integer> timesDriven = new HashMap<>();
    private final Map<String, Directions> roads = new HashMap<>();
    private final Set<String> noRoadNear = new HashSet<>();

    private final TravelMatrixProvider provider = new TravelMatrixProvider() {
        @Override
        public TravelMatrix matrixFor(List<Location> places) {
            throw new AssertionError("drives one at a time");
        }

        @Override
        public Drive driveBetween(Location from, Location to) {
            if (noRoadNear.contains(to.name())) {
                throw new UnroutablePlaceException(to.name(), "No road was found near those coordinates.");
            }
            String key = from.name() + ">" + to.name();
            timesDriven.merge(key, 1, Integer::sum);
            Drive drive = drives.get(key);
            if (drive == null) {
                throw new AssertionError("the test gives no drive " + key);
            }
            return drive;
        }

        @Override
        public Directions directionsBetween(Location from, Location to) {
            return roads.getOrDefault(from.name() + ">" + to.name(), Directions.NONE);
        }
    };

    private final PlacesAlongTheRoute placesAlong = new PlacesAlongTheRoute(search, provider);

    @Test
    void putsThePlacesInTheOrderOfTheTimeTheyAdd() {
        found.add(place("Far Fuel", -36.95, 174.75));
        found.add(place("Near Fuel", -36.901, 174.75));
        drive("Start", "Shops", 10, 600);
        drive("Start", "Near Fuel", 5, 300);
        drive("Near Fuel", "Shops", 5.2, 320);
        drive("Start", "Far Fuel", 7, 400);
        drive("Far Fuel", "Shops", 8, 500);

        List<PlaceAlong> along = placesAlong.find("fuel", List.of(START, SHOPS), OptionalInt.empty(), 8);

        assertThat(along).extracting(stop -> stop.place().name()).containsExactly("Near Fuel", "Far Fuel");
        assertThat(along.getFirst().secondsAdded()).hasValue(20);
        assertThat(along.getFirst().kmAdded()).isCloseTo(0.2, within(1e-9));
        assertThat(along.getLast().secondsAdded()).hasValue(300);
        assertThat(along.getLast().kmAdded()).isEqualTo(5);
    }

    @Test
    void putsThemInTheOrderOfTheDistanceTheyAddOnStraightLines() {
        found.add(place("Near Fuel", -36.901, 174.75));
        found.add(place("Far Fuel", -36.95, 174.75));
        straightLine("Start", "Shops", 10);
        straightLine("Start", "Near Fuel", 6);
        straightLine("Near Fuel", "Shops", 6);
        straightLine("Start", "Far Fuel", 5);
        straightLine("Far Fuel", "Shops", 5.5);

        List<PlaceAlong> along = placesAlong.find("fuel", List.of(START, SHOPS), OptionalInt.empty(), 8);

        assertThat(along).extracting(stop -> stop.place().name()).containsExactly("Far Fuel", "Near Fuel");
        assertThat(along).allSatisfy(stop -> assertThat(stop.secondsAdded()).isEmpty());
        assertThat(along.getFirst().kmAdded()).isEqualTo(0.5);
    }

    @Test
    void putsEachPlaceOnTheLegWhoseRoadPassesNearest() {
        // West to east, then south to north.
        road("Start", "Shops", point(-36.90, 174.70), point(-36.90, 174.75), point(-36.90, 174.80));
        road("Shops", "Work", point(-36.90, 174.80), point(-36.85, 174.80), point(-36.80, 174.80));
        found.add(place("Petrol", -36.85, 174.801));
        found.add(place("Cafe", -36.901, 174.72));
        drive("Shops", "Work", 12, 700);
        drive("Shops", "Petrol", 6, 350);
        drive("Petrol", "Work", 6.1, 360);
        drive("Start", "Shops", 10, 600);
        drive("Start", "Cafe", 2, 120);
        drive("Cafe", "Shops", 8.5, 540);

        List<PlaceAlong> along = placesAlong.find("p", List.of(START, SHOPS, WORK), OptionalInt.empty(), 8);

        assertThat(along).extracting(stop -> stop.place().name() + " on leg " + stop.leg())
                .containsExactly("Petrol on leg 1", "Cafe on leg 0");
        assertThat(roadLookedAlong).hasSize(6);
    }

    @Test
    void looksAlongTheOneLegAskedAndPutsEveryPlaceOnIt() {
        road("Start", "Shops", point(-36.90, 174.70), point(-36.90, 174.80));
        road("Shops", "Work", point(-36.90, 174.80), point(-36.80, 174.80));
        // Nearer the first leg, but only the second is looked along.
        found.add(place("Cafe", -36.901, 174.72));
        drive("Shops", "Work", 12, 700);
        drive("Shops", "Cafe", 9, 560);
        drive("Cafe", "Work", 14, 800);

        List<PlaceAlong> along = placesAlong.find("cafe", List.of(START, SHOPS, WORK), OptionalInt.of(1), 8);

        assertThat(along).extracting(PlaceAlong::leg).containsExactly(1);
        assertThat(along.getFirst().secondsAdded()).hasValue(660);
        assertThat(roadLookedAlong).containsExactly(
                new ReferencePoint(-36.90, 174.80), new ReferencePoint(-36.80, 174.80));
    }

    @Test
    void looksAlongAStraightLineBetweenTheEndsWhereThereIsNoRoad() {
        found.add(place("Cafe", -36.901, 174.72));
        straightLine("Start", "Shops", 10);
        straightLine("Start", "Cafe", 2);
        straightLine("Cafe", "Shops", 8.1);

        placesAlong.find("cafe", List.of(START, SHOPS), OptionalInt.empty(), 8);

        assertThat(roadLookedAlong).containsExactly(
                new ReferencePoint(-36.90, 174.70), new ReferencePoint(-36.90, 174.80));
    }

    @Test
    void leavesOutAPlaceWithNoRoadNearIt() {
        found.add(place("Island Cafe", -36.95, 174.75));
        found.add(place("Cafe", -36.901, 174.72));
        noRoadNear.add("Island Cafe");
        drive("Start", "Shops", 10, 600);
        drive("Start", "Cafe", 2, 120);
        drive("Cafe", "Shops", 8.1, 490);

        assertThat(placesAlong.find("cafe", List.of(START, SHOPS), OptionalInt.empty(), 8))
                .extracting(stop -> stop.place().name())
                .containsExactly("Cafe");
    }

    @Test
    void neverAddsLessThanNothing() {
        // On the road the leg follows anyway, where the engine meets the road at the place itself.
        found.add(place("Roadside Cafe", -36.90, 174.75));
        drive("Start", "Shops", 10, 600);
        drive("Start", "Roadside Cafe", 4.99, 299);
        drive("Roadside Cafe", "Shops", 5, 300);

        PlaceAlong along = placesAlong.find("cafe", List.of(START, SHOPS), OptionalInt.empty(), 8).getFirst();

        assertThat(along.kmAdded()).isZero();
        assertThat(along.secondsAdded()).hasValue(0);
    }

    @Test
    void drivesEachLegAsPlannedOnceHoweverManyPlacesAreOnIt() {
        IntStream.rangeClosed(1, 3).forEach(n -> {
            found.add(place("Cafe " + n, -36.901, 174.70 + n * 0.02));
            drive("Start", "Cafe " + n, 2 * n, 120 * n);
            drive("Cafe " + n, "Shops", 10 - 2 * n, 600 - 120 * n + 60);
        });
        drive("Start", "Shops", 10, 600);

        placesAlong.find("cafe", List.of(START, SHOPS), OptionalInt.empty(), 8);

        assertThat(timesDriven).containsEntry("Start>Shops", 1);
    }

    @Test
    void givesNoMoreThanItWasAskedFor() {
        IntStream.rangeClosed(1, 3).forEach(n -> {
            found.add(place("Cafe " + n, -36.901, 174.70 + n * 0.02));
            drive("Start", "Cafe " + n, 5, 300);
            drive("Cafe " + n, "Shops", 5, 300 + 60 * n);
        });
        drive("Start", "Shops", 10, 600);

        assertThat(placesAlong.find("cafe", List.of(START, SHOPS), OptionalInt.empty(), 2))
                .extracting(stop -> stop.place().name())
                .containsExactly("Cafe 1", "Cafe 2");
    }

    @Test
    void asksTheSearchForMorePlacesThanItGivesBack() {
        drive("Start", "Shops", 10, 600);

        placesAlong.find("cafe", List.of(START, SHOPS), OptionalInt.empty(), 3);
        assertThat(placesAskedFor).isEqualTo(PlacesAlongTheRoute.CANDIDATES);

        placesAlong.find("cafe", List.of(START, SHOPS), OptionalInt.empty(), 20);
        assertThat(placesAskedFor).isEqualTo(20);
    }

    @Test
    void refusesALegThatIsNotOnTheRoute() {
        assertThatThrownBy(() -> placesAlong.find("cafe", List.of(START, SHOPS), OptionalInt.of(1), 8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no leg 1");
    }

    @Test
    void refusesARouteOfOnePlace() {
        assertThatThrownBy(() -> placesAlong.find("cafe", List.of(START), OptionalInt.empty(), 8))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thinsARoadToPointsAHundredMetresApartAndKeepsItsEnds() {
        // A kilometre due north, a point every 10 m.
        List<GeoPoint> road = IntStream.rangeClosed(0, 100)
                .mapToObj(n -> point(-36.90 + n * 0.00009, 174.70))
                .toList();

        List<GeoPoint> thinned = PlacesAlongTheRoute.thinned(road);

        assertThat(thinned.getFirst()).isEqualTo(road.getFirst());
        assertThat(thinned.getLast()).isEqualTo(road.getLast());
        assertThat(thinned).hasSizeBetween(10, 12);
        for (int at = 1; at < thinned.size() - 1; at++) {
            assertThat(Flat.metresBetween(thinned.get(at - 1), thinned.get(at)))
                    .isGreaterThanOrEqualTo(PlacesAlongTheRoute.THINNED_TO_METRES);
        }
    }

    private static FoundPlace place(String name, double latitude, double longitude) {
        return FoundPlace.poi(name, null, "Testville", null, latitude, longitude);
    }

    private static GeoPoint point(double latitude, double longitude) {
        return new GeoPoint(latitude, longitude);
    }

    private void drive(String from, String to, double km, double seconds) {
        drives.put(from + ">" + to, new Drive(km, OptionalDouble.of(seconds)));
    }

    private void straightLine(String from, String to, double km) {
        drives.put(from + ">" + to, new Drive(km, OptionalDouble.empty()));
    }

    private void road(String from, String to, GeoPoint... points) {
        roads.put(from + ">" + to, new Directions(List.of(points), List.of()));
    }
}
