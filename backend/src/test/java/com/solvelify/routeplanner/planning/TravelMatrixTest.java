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

    /** A hill between the two places: 80 m up one side, 50 m down the other. */
    private static final ClimbAndDescent[][] CLIMBS = {
        {ClimbAndDescent.LEVEL, new ClimbAndDescent(80, 50)},
        {new ClimbAndDescent(50, 80), ClimbAndDescent.LEVEL},
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

    // Climbs.

    @Test
    void givesTheClimbEachWayWhenItWasToldTheClimbs() {
        TravelMatrix matrix = new TravelMatrix(KM, SECONDS, CLIMBS);

        assertThat(matrix.climbAndDescent(0, 1)).contains(new ClimbAndDescent(80, 50));
        assertThat(matrix.climbAndDescent(1, 0)).contains(new ClimbAndDescent(50, 80));
    }

    @Test
    void hasNoClimbToGiveWhenTheRoadsHaveNoHeights() {
        assertThat(new TravelMatrix(KM, SECONDS).climbAndDescent(0, 1)).isEmpty();
        assertThat(new TravelMatrix(KM).climbAndDescent(0, 1)).isEmpty();
    }

    @Test
    void tellsALevelDriveFromOneItKnowsNothingAbout() {
        TravelMatrix matrix = new TravelMatrix(KM, SECONDS, CLIMBS);

        // Known, and nought. Not the same as empty.
        assertThat(matrix.climbAndDescent(0, 0)).contains(ClimbAndDescent.LEVEL);
    }

    @Test
    void stillGivesDistancesAndTimesWhenItAlsoHasClimbs() {
        TravelMatrix matrix = new TravelMatrix(KM, SECONDS, CLIMBS);

        assertThat(matrix.distanceKm(1, 0)).isEqualTo(12);
        assertThat(matrix.durationSeconds(1, 0)).hasValue(750);
    }

    @Test
    void refusesClimbsForADifferentNumberOfPlaces() {
        ClimbAndDescent[][] climbsForOne = {{ClimbAndDescent.LEVEL}};

        assertThatThrownBy(() -> new TravelMatrix(KM, SECONDS, climbsForOne))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("climbs for 1")
                .hasMessageContaining("2");
    }

    @Test
    void refusesTimesForADifferentNumberOfPlacesWhenItHasClimbsToo() {
        double[][] secondsForThree = {{0, 1, 2}, {1, 0, 2}, {2, 1, 0}};

        assertThatThrownBy(() -> new TravelMatrix(KM, secondsForThree, CLIMBS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("durations for 3");
    }

    @Test
    void isNotChangedByWhatHappensLaterToTheClimbsItWasGiven() {
        ClimbAndDescent[][] climbs = {
            {ClimbAndDescent.LEVEL, new ClimbAndDescent(80, 50)},
            {new ClimbAndDescent(50, 80), ClimbAndDescent.LEVEL},
        };
        TravelMatrix matrix = new TravelMatrix(KM, SECONDS, climbs);

        climbs[0][1] = new ClimbAndDescent(999, 999);

        assertThat(matrix.climbAndDescent(0, 1)).contains(new ClimbAndDescent(80, 50));
    }

    @Test
    void stillHandsTheSolverDistancesWhenItHasClimbs() {
        TravelMatrix matrix = new TravelMatrix(KM, SECONDS, CLIMBS);

        assertThat(matrix.asCostMatrix()).isDeepEqualTo(KM);
    }
}
