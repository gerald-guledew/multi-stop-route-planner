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
