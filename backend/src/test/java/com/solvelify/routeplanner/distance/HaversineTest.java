package com.solvelify.routeplanner.distance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.solvelify.routeplanner.planning.Location;
import org.junit.jupiter.api.Test;

class HaversineTest {

    private static final Location SKY_TOWER = new Location("Sky Tower", -36.8485, 174.7621);
    private static final Location AIRPORT = new Location("Airport", -37.0082, 174.7850);

    @Test
    void measuresSkyTowerToTheAirport() {
        // Checked outside this codebase, so the test is not just agreeing with itself.
        assertThat(Haversine.distanceKm(SKY_TOWER, AIRPORT)).isCloseTo(17.87, within(0.01));
    }

    @Test
    void isZeroBetweenAPlaceAndItself() {
        assertThat(Haversine.distanceKm(SKY_TOWER, SKY_TOWER)).isZero();
    }

    @Test
    void readsTheSameInBothDirections() {
        assertThat(Haversine.distanceKm(SKY_TOWER, AIRPORT))
                .isEqualTo(Haversine.distanceKm(AIRPORT, SKY_TOWER));
    }
}
