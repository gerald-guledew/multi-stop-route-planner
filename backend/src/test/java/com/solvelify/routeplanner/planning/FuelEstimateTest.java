package com.solvelify.routeplanner.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class FuelEstimateTest {

    private static final Location HOME = new Location("Home", -36.9146, 174.8392);
    private static final Location OFFICE = new Location("Office", -36.85, 174.76);
    private static final Location SHOP = new Location("Shop", -36.90, 174.80);

    /** 12 km out, 8 km across and 20 km back: 40 km. */
    private static final RoutePlan ROUND_TRIP = new RoutePlan(
            List.of(HOME, OFFICE, SHOP, HOME),
            List.of(
                    new RoutePlan.Leg("Home", "Office", 12, List.of()),
                    new RoutePlan.Leg("Office", "Shop", 8, List.of()),
                    new RoutePlan.Leg("Shop", "Home", 20, List.of())),
            40, 40, 2);

    @Test
    void takesTheDashboardFigureForEveryKilometre() {
        FuelEstimate estimate = FuelEstimate.of(ROUND_TRIP, new FuelUse(7.5));

        // 7.5 L for every 100 km: 0.9 L for 12 km, 0.6 L for 8 km, 1.5 L for 20 km.
        assertThat(estimate.legLitres()).satisfiesExactly(
                first -> assertThat(first).isCloseTo(0.9, within(1e-9)),
                second -> assertThat(second).isCloseTo(0.6, within(1e-9)),
                third -> assertThat(third).isCloseTo(1.5, within(1e-9)));
        assertThat(estimate.totalLitres()).isCloseTo(3.0, within(1e-9));
    }

    @Test
    void doesNotCountTheHillsOfRoadsReadWithoutHeights() {
        FuelEstimate estimate = FuelEstimate.of(ROUND_TRIP, new FuelUse(7.5, OptionalDouble.of(1225)));

        assertThat(estimate.countsHills()).isFalse();
        assertThat(estimate.totalLitres()).isCloseTo(3.0, within(1e-9));
    }

    @Test
    void countsTheHillsOfEachLegWhenTheWeightIsKnown() {
        // Up 100 m to the office, level to the shop, and the 100 m down again on the way home,
        // gently enough to get back what the climb cost.
        RoutePlan hilly = new RoutePlan(
                List.of(HOME, OFFICE, SHOP, HOME),
                List.of(
                        leg("Home", "Office", 12, new ClimbAndDescent(100, 0)),
                        leg("Office", "Shop", 8, ClimbAndDescent.LEVEL),
                        leg("Shop", "Home", 20, new ClimbAndDescent(0, 100, List.of(new ClimbAndDescent.Descent(100, 5000))))),
                40, OptionalDouble.empty(), Optional.of(new ClimbAndDescent(100, 100)), 40, 2);
        double hundredMetresUp = 1300 * 9.81 * 100 / (0.30 * 33e6);

        FuelEstimate estimate = FuelEstimate.of(hilly, new FuelUse(7.5, OptionalDouble.of(1225)));

        assertThat(estimate.countsHills()).isTrue();
        assertThat(estimate.legLitres()).satisfiesExactly(
                first -> assertThat(first).isCloseTo(0.9 + hundredMetresUp, within(1e-9)),
                second -> assertThat(second).isCloseTo(0.6, within(1e-9)),
                third -> assertThat(third).isCloseTo(1.5 - hundredMetresUp, within(1e-9)));
        assertThat(estimate.totalLitres()).isCloseTo(3.0, within(1e-9));
    }

    @Test
    void givesWhatTheShortestOrderTakesWhenThePlanIsHeldAgainstIt() {
        // The shortest order is 36 km and comes down its 100 m in 1 km, 10%, too steep to give
        // back more than that kilometre takes on the level.
        RoutePlan heldAgainstTheShortest = new RoutePlan(
                ROUND_TRIP.route(), ROUND_TRIP.legs(), 40, OptionalDouble.empty(), Optional.empty(), 40, 2,
                Optional.of(new RoutePlan.ShortestOrder(36, OptionalDouble.empty(), Optional.of(
                        new ClimbAndDescent(100, 100, List.of(new ClimbAndDescent.Descent(100, 1000)))))));
        double hundredMetresUp = 1300 * 9.81 * 100 / (0.30 * 33e6);

        FuelEstimate estimate = FuelEstimate.of(heldAgainstTheShortest, new FuelUse(7.5, OptionalDouble.of(1225)));

        assertThat(estimate.shortestOrderLitres()).hasValueCloseTo(2.7 + hundredMetresUp - 0.075, within(1e-9));
    }

    @Test
    void givesNothingForTheShortestOrderWhenThePlanIsTheShortestOrder() {
        assertThat(FuelEstimate.of(ROUND_TRIP, new FuelUse(7.5)).shortestOrderLitres()).isEmpty();
    }

    @Test
    void takesTheTownFigureForEachLegsKilometresInTown() {
        // 12 km all in town, 8 km all on the open road, and 20 km half and half.
        RoutePlan onRoads = new RoutePlan(
                ROUND_TRIP.route(),
                List.of(inTown("Home", "Office", 12, 12), inTown("Office", "Shop", 8, 0), inTown("Shop", "Home", 20, 10)),
                40, 40, 2);

        FuelEstimate estimate = FuelEstimate.of(onRoads, new FuelUse(7.5, 9.0, 6.0, OptionalDouble.empty()));

        assertThat(estimate.countsSpeed()).isTrue();
        assertThat(estimate.legLitres()).satisfiesExactly(
                first -> assertThat(first).isCloseTo(1.08, within(1e-9)),
                second -> assertThat(second).isCloseTo(0.48, within(1e-9)),
                third -> assertThat(third).isCloseTo(1.5, within(1e-9)));
    }

    @Test
    void doesNotCountTheSpeedOnStraightLines() {
        assertThat(FuelEstimate.of(ROUND_TRIP, new FuelUse(7.5)).countsSpeed()).isFalse();
    }

    @Test
    void givesWhatTheShortestOrderTakesInTownAndOnTheOpenRoad() {
        RoutePlan heldAgainstTheShortest = new RoutePlan(
                ROUND_TRIP.route(), ROUND_TRIP.legs(), 40, OptionalDouble.empty(), Optional.empty(), 40, 2,
                Optional.of(new RoutePlan.ShortestOrder(36, OptionalDouble.empty(), Optional.empty(), OptionalDouble.of(6))));

        FuelEstimate estimate = FuelEstimate.of(heldAgainstTheShortest, new FuelUse(7.5, 9.0, 6.0, OptionalDouble.empty()));

        // 6 km in town at 9 and 30 km on the open road at 6.
        assertThat(estimate.shortestOrderLitres()).hasValueCloseTo(0.54 + 1.8, within(1e-9));
    }

    @Test
    void givesWhatTheOtherChoiceTakes() {
        RoutePlan besideTheFastest = new RoutePlan(
                ROUND_TRIP.route(), ROUND_TRIP.legs(), 40, OptionalDouble.empty(), Optional.empty(), 40, 2,
                Optional.empty(),
                Optional.of(new RoutePlan.OtherChoice(
                        ChosenFor.TIME, 42, OptionalDouble.of(2400), Optional.empty(), OptionalDouble.of(2))));

        FuelEstimate estimate = FuelEstimate.of(besideTheFastest, new FuelUse(7.5, 9.0, 6.0, OptionalDouble.empty()));

        // 2 km in town at 9 and 40 km on the open road at 6.
        assertThat(estimate.otherChoiceLitres()).hasValueCloseTo(0.18 + 2.4, within(1e-9));
    }

    @Test
    void givesNothingForTheOtherChoiceWhenThePlanGivesNone() {
        assertThat(FuelEstimate.of(ROUND_TRIP, new FuelUse(7.5)).otherChoiceLitres()).isEmpty();
    }

    private static RoutePlan.Leg inTown(String from, String to, double distanceKm, double kmInTown) {
        return new RoutePlan.Leg(from, to, distanceKm, OptionalDouble.empty(), Optional.empty(),
                OptionalDouble.of(kmInTown), List.of(), List.of());
    }

    private static RoutePlan.Leg leg(String from, String to, double distanceKm, ClimbAndDescent hills) {
        return new RoutePlan.Leg(from, to, distanceKm, OptionalDouble.empty(), Optional.of(hills), List.of());
    }

    @Test
    void takesNothingForADriveOfNoLength() {
        assertThat(new FuelUse(7.5).litresFor(0)).isZero();
    }

    @Test
    void refusesAFigureThatIsNotAnAmountOfFuel() {
        for (double notFuel : new double[] {0, -7.5, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatThrownBy(() -> new FuelUse(notFuel)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
