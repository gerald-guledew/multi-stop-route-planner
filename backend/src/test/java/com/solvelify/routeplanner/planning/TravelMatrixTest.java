package com.solvelify.routeplanner.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.Optional;
import java.util.OptionalDouble;
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
    void saysWhetherItKnowsTheClimb() {
        assertThat(new TravelMatrix(KM, SECONDS, CLIMBS).knowsClimb()).isTrue();
        assertThat(new TravelMatrix(KM, SECONDS).knowsClimb()).isFalse();
        assertThat(new TravelMatrix(KM).knowsClimb()).isFalse();
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

    // The table for an order chosen for driving time.

    @Test
    void handsTheSolverTheTimesForAnOrderChosenForTime() {
        TravelMatrix matrix = new TravelMatrix(KM, SECONDS, CLIMBS);

        assertThat(matrix.asCostMatrixOfDrivingTime()).hasValueSatisfying(
                cost -> assertThat(cost).isDeepEqualTo(SECONDS));
    }

    @Test
    void hasNoTimesToHandTheSolverOnStraightLines() {
        assertThat(new TravelMatrix(KM).asCostMatrixOfDrivingTime()).isEmpty();
    }

    @Test
    void keepsItsTimesAsTheyWereWhenTheTableIsChanged() {
        TravelMatrix matrix = new TravelMatrix(KM, SECONDS);

        matrix.asCostMatrixOfDrivingTime().orElseThrow()[0][1] = 1;

        assertThat(matrix.durationSeconds(0, 1)).hasValue(600);
    }

    // The table for an order chosen for distance and climb together.

    @Test
    void countsEveryMetreClimbedAsSoManyMetresOfRoad() {
        TravelMatrix matrix = new TravelMatrix(KM, SECONDS, CLIMBS);

        double[][] cost = matrix.asCostMatrixCountingClimb(10).orElseThrow();

        // 80 m up at 10 m of road each is 800 m on top of the 10 km. The way back climbs 50 m.
        assertThat(cost[0][1]).isCloseTo(10.8, within(1e-9));
        assertThat(cost[1][0]).isCloseTo(12.5, within(1e-9));
        // Staying where you are costs nothing either way.
        assertThat(cost[0][0]).isZero();
        assertThat(cost[1][1]).isZero();
    }

    @Test
    void countsNothingForComingDown() {
        ClimbAndDescent[][] downhillOneWay = {
            {ClimbAndDescent.LEVEL, new ClimbAndDescent(0, 70)},
            {new ClimbAndDescent(70, 0), ClimbAndDescent.LEVEL},
        };
        TravelMatrix matrix = new TravelMatrix(KM, SECONDS, downhillOneWay);

        double[][] cost = matrix.asCostMatrixCountingClimb(10).orElseThrow();

        assertThat(cost[0][1]).isEqualTo(10);
        assertThat(cost[1][0]).isCloseTo(12.7, within(1e-9));
    }

    @Test
    void isTheDistancesAgainWhenAMetreClimbedCountsAsNoRoadAtAll() {
        TravelMatrix matrix = new TravelMatrix(KM, SECONDS, CLIMBS);

        assertThat(matrix.asCostMatrixCountingClimb(0).orElseThrow()).isDeepEqualTo(KM);
    }

    @Test
    void hasNoClimbToCountWhenTheRoadsHaveNoHeights() {
        assertThat(new TravelMatrix(KM, SECONDS).asCostMatrixCountingClimb(10)).isEmpty();
        assertThat(new TravelMatrix(KM).asCostMatrixCountingClimb(10)).isEmpty();
    }

    // The table for an order chosen for fuel.

    /** A car of 1,225 kg at 7.5 L/100 km. With the driver it weighs 1,300 kg. */
    private static final FuelUse A_MEDIUM_CAR = new FuelUse(7.5, OptionalDouble.of(1225));

    @Test
    void givesWhatEachDriveUsesUpForGood() {
        TravelMatrix matrix = new TravelMatrix(KM, SECONDS, CLIMBS);

        double[][] cost = matrix.asCostMatrixOfFuel(A_MEDIUM_CAR).orElseThrow();

        assertThat(cost[0][1]).isEqualTo(A_MEDIUM_CAR.litresUsedUpFor(10, Optional.of(CLIMBS[0][1])));
        assertThat(cost[1][0]).isEqualTo(A_MEDIUM_CAR.litresUsedUpFor(12, Optional.of(CLIMBS[1][0])));
        // Up 80 m and down 50 m, gently enough over 10 km to get back all that cost. The 30 m it
        // ends higher is kept as height. So it uses up what its 10 km take on the level.
        assertThat(cost[0][1]).isCloseTo(0.75, within(1e-9));
        assertThat(cost[0][0]).isZero();
    }

    @Test
    void hasNoFuelTableWhenTheHillsCannotBeCounted() {
        // The fuel then goes with the distance, and the shortest order already takes the least.
        assertThat(new TravelMatrix(KM, SECONDS).asCostMatrixOfFuel(A_MEDIUM_CAR)).isEmpty();
        assertThat(new TravelMatrix(KM).asCostMatrixOfFuel(A_MEDIUM_CAR)).isEmpty();
        assertThat(new TravelMatrix(KM, SECONDS, CLIMBS).asCostMatrixOfFuel(new FuelUse(7.5))).isEmpty();
    }

    @Test
    void keepsItsDistancesAsTheyWereAfterCountingTheClimb() {
        TravelMatrix matrix = new TravelMatrix(KM, SECONDS, CLIMBS);

        matrix.asCostMatrixCountingClimb(10);

        assertThat(matrix.distanceKm(0, 1)).isEqualTo(10);
        assertThat(matrix.asCostMatrix()).isDeepEqualTo(KM);
    }
}
