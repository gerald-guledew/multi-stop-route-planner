package com.solvelify.routeplanner.distance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.solvelify.routeplanner.planning.Location;
import com.solvelify.routeplanner.planning.TravelMatrix;
import java.util.List;
import org.junit.jupiter.api.Test;

class HaversineTravelMatrixProviderTest {

    private final HaversineTravelMatrixProvider provider = new HaversineTravelMatrixProvider();

    private static final List<Location> PLACES = List.of(
            new Location("Sky Tower", -36.8485, 174.7621),
            new Location("Takapuna", -36.7870, 174.7740),
            new Location("Airport", -37.0082, 174.7850));

    @Test
    void buildsOneRowAndColumnPerPlace() {
        assertThat(provider.matrixFor(PLACES).size()).isEqualTo(3);
    }

    @Test
    void measuresZeroAlongTheDiagonal() {
        TravelMatrix matrix = provider.matrixFor(PLACES);

        for (int place = 0; place < matrix.size(); place++) {
            assertThat(matrix.distanceKm(place, place)).isZero();
        }
    }

    @Test
    void readsTheSameInBothDirections() {
        TravelMatrix matrix = provider.matrixFor(PLACES);

        for (int from = 0; from < matrix.size(); from++) {
            for (int to = 0; to < matrix.size(); to++) {
                assertThat(matrix.distanceKm(from, to)).isEqualTo(matrix.distanceKm(to, from));
            }
        }
    }

    @Test
    void agreesWithTheKnownSkyTowerToAirportDistance() {
        TravelMatrix matrix = provider.matrixFor(PLACES);

        assertThat(matrix.distanceKm(0, 2)).isCloseTo(17.87, within(0.01));
    }

    @Test
    void doesNotLetCallersChangeTheMatrixUnderneathIt() {
        TravelMatrix matrix = provider.matrixFor(PLACES);
        double[][] costs = matrix.asCostMatrix();

        costs[0][1] = 999;

        assertThat(matrix.distanceKm(0, 1)).isNotEqualTo(999);
    }
}
