package com.solvelify.routeplanner.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TravelMatrixTest {

    private static final double[][] KM = {
        {0, 10},
        {12, 0},
    };

    private static final double[][] SECONDS = {
        {0, 600},
        {750, 0},
    };

    @Test
    void givesTheDistanceEachWay() {
        TravelMatrix matrix = new TravelMatrix(KM, SECONDS);

        assertThat(matrix.distanceKm(0, 1)).isEqualTo(10);
        assertThat(matrix.distanceKm(1, 0)).isEqualTo(12);
    }

    @Test
    void givesTheTimeEachWayWhenItWasToldTheTimes() {
        TravelMatrix matrix = new TravelMatrix(KM, SECONDS);

        assertThat(matrix.durationSeconds(0, 1)).hasValue(600);
        assertThat(matrix.durationSeconds(1, 0)).hasValue(750);
    }

    @Test
    void hasNoTimeToGiveWhenItWasToldOnlyDistances() {
        TravelMatrix matrix = new TravelMatrix(KM);

        assertThat(matrix.durationSeconds(0, 1)).isEmpty();
    }

    @Test
    void refusesTimesForADifferentNumberOfPlaces() {
        double[][] secondsForThree = {{0, 1, 2}, {1, 0, 2}, {2, 1, 0}};

        assertThatThrownBy(() -> new TravelMatrix(KM, secondsForThree))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3")
                .hasMessageContaining("2");
    }

    @Test
    void isNotChangedByWhatHappensLaterToTheTablesItWasGiven() {
        double[][] km = {{0, 10}, {12, 0}};
        double[][] seconds = {{0, 600}, {750, 0}};
        TravelMatrix matrix = new TravelMatrix(km, seconds);

        km[0][1] = 99;
        seconds[0][1] = 99;

        assertThat(matrix.distanceKm(0, 1)).isEqualTo(10);
        assertThat(matrix.durationSeconds(0, 1)).hasValue(600);
    }

    @Test
    void handsTheSolverDistancesToMinimiseAndNotTimes() {
        TravelMatrix matrix = new TravelMatrix(KM, SECONDS);

        assertThat(matrix.asCostMatrix()).isDeepEqualTo(KM);
    }
}
