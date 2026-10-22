package com.solvelify.routeplanner.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.solvelify.routeplanner.planning.ClimbAndDescent.Descent;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class FuelUseTest {

    /** 1,225 kg and the driver: 1,300 kg. */
    private static final FuelUse MEDIUM_CAR = new FuelUse(7.5, OptionalDouble.of(1225));

    /** 1,300 kg lifted 100 m, at 30% of 33 MJ a litre. */
    private static final double HUNDRED_METRES_UP = 1300 * 9.81 * 100 / (0.30 * 33e6);

    @Test
    void takesTheFigureForEveryKilometreWithoutTheWeight() {
        FuelUse weightUnknown = new FuelUse(7.5);

        assertThat(weightUnknown.countsHills()).isFalse();
        assertThat(weightUnknown.litresFor(10, Optional.of(new ClimbAndDescent(300, 0)))).isCloseTo(0.75, within(1e-9));
    }

    @Test
    void takesTheFigureForEveryKilometreWithoutTheHills() {
        assertThat(MEDIUM_CAR.litresFor(10, Optional.empty())).isCloseTo(0.75, within(1e-9));
    }

    @Test
    void addsTheWorkOfLiftingTheCarAndTheDriver() {
        // About 0.13 L for every 100 m up.
        assertThat(HUNDRED_METRES_UP).isCloseTo(0.129, within(0.001));
        assertThat(MEDIUM_CAR.litresFor(10, Optional.of(new ClimbAndDescent(100, 0))))
                .isCloseTo(0.75 + HUNDRED_METRES_UP, within(1e-9));
    }

    @Test
    void takesOneAndAHalfToTwiceTheFuelOfLevelRoadUpAFourPercentClimb() {
        // Measured on the road for a car up an average of 4%: 1.5 to 2 times the fuel.
        for (FuelUse car : List.of(
                new FuelUse(5.5, OptionalDouble.of(1000)),
                new FuelUse(7.0, OptionalDouble.of(1300)),
                new FuelUse(9.5, OptionalDouble.of(1800)))) {
            double upFourPercent = car.litresFor(1, Optional.of(new ClimbAndDescent(40, 0)));

            assertThat(upFourPercent / car.litresFor(1)).isBetween(1.5, 2.0);
        }
    }

    @Test
    void getsBackOnAGentleDescentWhatTheClimbCost() {
        // Over a hill: 100 m up and 100 m down, 2% each way.
        ClimbAndDescent overTheHill = new ClimbAndDescent(100, 100, List.of(new Descent(100, 5000)));

        assertThat(MEDIUM_CAR.litresFor(10, Optional.of(overTheHill))).isCloseTo(0.75, within(1e-9));
    }

    @Test
    void getsBackNoMoreThanASteepDescentTakesOnTheLevel() {
        // 100 m down in 1 km is 10%. On the level that kilometre takes 0.075 L, and that is all
        // the slope can save: the rest is braked away.
        ClimbAndDescent steep = new ClimbAndDescent(100, 100, List.of(new Descent(100, 1000)));

        assertThat(MEDIUM_CAR.litresFor(10, Optional.of(steep))).isCloseTo(0.75 + HUNDRED_METRES_UP - 0.075, within(1e-9));
    }

    @Test
    void addsUpEachDescentOnItsOwn() {
        ClimbAndDescent gentleAndSteep = new ClimbAndDescent(0, 150,
                List.of(new Descent(50, 5000), new Descent(100, 1000)));

        double gentle = HUNDRED_METRES_UP / 2;
        assertThat(MEDIUM_CAR.litresFor(10, Optional.of(gentleAndSteep))).isCloseTo(0.75 - gentle - 0.075, within(1e-9));
    }

    @Test
    void takesTheWholeDriveAsTheRoadOfADescentKnownOnlyAsATotal() {
        // 100 m down over 10 km is 1%, gentle: given back in full.
        assertThat(MEDIUM_CAR.litresFor(10, Optional.of(new ClimbAndDescent(0, 100))))
                .isCloseTo(0.75 - HUNDRED_METRES_UP, within(1e-9));
        // 1,000 m down over 10 km: no more than the 10 km take on the level.
        assertThat(MEDIUM_CAR.litresFor(10, Optional.of(new ClimbAndDescent(0, 1000)))).isZero();
    }

    // The fuel a drive uses up for good, which an order chosen for fuel is chosen by.

    @Test
    void usesUpNothingForAClimbItKeepsAsHeight() {
        // Up 100 m and not down: the car keeps the climb, and the next drive down gets it back.
        assertThat(MEDIUM_CAR.litresUsedUpFor(10, Optional.of(new ClimbAndDescent(100, 0)))).isCloseTo(0.75, within(1e-9));
    }

    @Test
    void usesUpWhatASteepDescentBrakesAway() {
        // 100 m down in 1 km, as above: the slope saves 0.075 L of what the height was worth.
        ClimbAndDescent steepDown = new ClimbAndDescent(0, 100, List.of(new Descent(100, 1000)));

        assertThat(MEDIUM_CAR.litresUsedUpFor(10, Optional.of(steepDown)))
                .isCloseTo(0.75 + HUNDRED_METRES_UP - 0.075, within(1e-9));
    }

    @Test
    void usesUpWhatADriveTakesWhenItEndsAsHighAsItBegan() {
        ClimbAndDescent steepOverTheHill = new ClimbAndDescent(100, 100, List.of(new Descent(100, 1000)));

        assertThat(MEDIUM_CAR.litresUsedUpFor(10, Optional.of(steepOverTheHill)))
                .isCloseTo(MEDIUM_CAR.litresFor(10, Optional.of(steepOverTheHill)), within(1e-9));
    }

    @Test
    void usesUpTheFigureForEveryKilometreWhenTheHillsCannotBeCounted() {
        assertThat(new FuelUse(7.5).litresUsedUpFor(10, Optional.of(new ClimbAndDescent(0, 300)))).isCloseTo(0.75, within(1e-9));
        assertThat(MEDIUM_CAR.litresUsedUpFor(10, Optional.empty())).isCloseTo(0.75, within(1e-9));
    }

    @Test
    void refusesAWeightThatIsNotOne() {
        for (double notAWeight : new double[] {0, -1200, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatThrownBy(() -> new FuelUse(7.5, OptionalDouble.of(notAWeight)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
