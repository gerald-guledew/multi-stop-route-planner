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

    // Town and the open road.

    @Test
    void splitsOneFigureBetweenTownAndTheOpenRoadTheUsualWay() {
        FuelUse oneFigure = new FuelUse(7.5);

        // Town 1.6 times the open road, and the two weighed as the approval test weighs them come to
        // the one figure: 9.83 in town and 6.14 on the open road.
        assertThat(oneFigure.townLitresPer100Km() / oneFigure.openRoadLitresPer100Km()).isCloseTo(1.6, within(1e-9));
        assertThat(FuelUse.TOWN_SHARE_OF_THE_TEST * oneFigure.townLitresPer100Km()
                + (1 - FuelUse.TOWN_SHARE_OF_THE_TEST) * oneFigure.openRoadLitresPer100Km()).isCloseTo(7.5, within(1e-9));
        assertThat(oneFigure.townLitresPer100Km()).isCloseTo(9.83, within(0.01));
        assertThat(oneFigure.openRoadLitresPer100Km()).isCloseTo(6.14, within(0.01));
    }

    @Test
    void splitsOneFigureAsTheCarsOwnFiguresAreSplit() {
        // A 2020 Corolla 2.0 sold new: 7.5 in town, 5.1 on the open road and 6.0 combined.
        FuelUse corolla = FuelUse.withTownOverOpenRoad(6.0, 7.5 / 5.1, OptionalDouble.empty());

        assertThat(corolla.townLitresPer100Km()).isCloseTo(7.5, within(0.05));
        assertThat(corolla.openRoadLitresPer100Km()).isCloseTo(5.1, within(0.05));
    }

    @Test
    void takesTheTownFigureInTownAndTheOpenRoadsForTheRest() {
        FuelUse car = new FuelUse(6.0, 7.5, 5.1, OptionalDouble.empty());

        // 4 km in town at 7.5 and 6 km on the open road at 5.1: 0.3 L and 0.306 L.
        assertThat(car.litresOnTheLevel(10, OptionalDouble.of(4))).isCloseTo(0.606, within(1e-9));
        assertThat(car.litresFor(10, OptionalDouble.of(4), Optional.empty())).isCloseTo(0.606, within(1e-9));
        assertThat(car.litresUsedUpFor(10, OptionalDouble.of(4), Optional.empty())).isCloseTo(0.606, within(1e-9));
    }

    @Test
    void takesTheOneFigureWhereTheRoadsAreNotKnown() {
        FuelUse car = new FuelUse(6.0, 7.5, 5.1, OptionalDouble.empty());

        assertThat(car.litresOnTheLevel(10, OptionalDouble.empty())).isCloseTo(0.6, within(1e-9));
    }

    @Test
    void countsTheHillsOnTopOfTownAndTheOpenRoad() {
        FuelUse car = new FuelUse(7.5, 9.0, 6.6, OptionalDouble.of(1225));

        // All 10 km in town, and 100 m up.
        assertThat(car.litresFor(10, OptionalDouble.of(10), Optional.of(new ClimbAndDescent(100, 0))))
                .isCloseTo(0.9 + HUNDRED_METRES_UP, within(1e-9));
    }

    @Test
    void givesBackOnASteepDescentWhatItsStretchTakesAtTheOneFigure() {
        // 100 m down in 1 km, in town. At the one figure that kilometre takes 0.075 L.
        FuelUse car = new FuelUse(7.5, 9.0, 6.6, OptionalDouble.of(1225));
        ClimbAndDescent steep = new ClimbAndDescent(100, 100, List.of(new Descent(100, 1000)));

        assertThat(car.litresFor(10, OptionalDouble.of(10), Optional.of(steep)))
                .isCloseTo(0.9 + HUNDRED_METRES_UP - 0.075, within(1e-9));
    }

    @Test
    void refusesATownOrOpenRoadFigureThatIsNotAnAmountOfFuel() {
        for (double notFuel : new double[] {0, -6, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatThrownBy(() -> new FuelUse(7.5, notFuel, 6.0, OptionalDouble.empty()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new FuelUse(7.5, 9.0, notFuel, OptionalDouble.empty()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
