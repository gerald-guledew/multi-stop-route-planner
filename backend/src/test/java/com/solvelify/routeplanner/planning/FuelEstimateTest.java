package com.solvelify.routeplanner.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
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
