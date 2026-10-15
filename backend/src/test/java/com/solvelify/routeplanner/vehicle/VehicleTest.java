package com.solvelify.routeplanner.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.solvelify.routeplanner.cars.Car;
import com.solvelify.routeplanner.cars.CarVariant;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class VehicleTest {

    private static final Car SWIFT = new Car("SUZUKI", "SWIFT", 2018, new BigDecimal("1.2"));
    private static final CarVariant SWIFT_FOUND = new CarVariant(SWIFT, 4.8, 1896);

    @Test
    void takesAnyFigureFromTwoToThirtyLitres() {
        assertThat(new Vehicle(2, VehicleSize.SMALL).getLitresPer100Km()).isEqualTo(2);
        assertThat(new Vehicle(30, VehicleSize.LARGE).getLitresPer100Km()).isEqualTo(30);
    }

    @Test
    void refusesAFigureNoPetrolCarShows() {
        for (double notAFigure : new double[] {1.9, 30.1, 0, -7.5, Double.NaN}) {
            assertThatThrownBy(() -> new Vehicle(notAFigure, VehicleSize.MEDIUM))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between 2.0 and 30.0");
        }
    }

    @Test
    void needsASize() {
        assertThatThrownBy(() -> new Vehicle(7.5, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aVehicleEnteredByHandGoesByItsDashboardAndIsNoCarFromTheRegister() {
        Vehicle entered = new Vehicle(7.5, VehicleSize.MEDIUM);

        assertThat(entered.getFigureFrom()).isEqualTo(FigureFrom.DASHBOARD);
        assertThat(entered.getCar()).isEmpty();
    }

    @Test
    void aCarFromTheRegisterGoesByTheRegistersFigure() {
        Vehicle found = new Vehicle(SWIFT_FOUND, null);

        assertThat(found.getLitresPer100Km()).isEqualTo(4.8);
        assertThat(found.getFigureFrom()).isEqualTo(FigureFrom.REGISTER);
        assertThat(found.getCar()).contains(SWIFT);
        assertThat(found.getSize()).isNull();
    }

    @Test
    void aCarFromTheRegisterGoesByItsDashboardWhenThatIsGiven() {
        Vehicle found = new Vehicle(SWIFT_FOUND, 5.6);

        assertThat(found.getLitresPer100Km()).isEqualTo(5.6);
        assertThat(found.getFigureFrom()).isEqualTo(FigureFrom.DASHBOARD);
        assertThat(found.getCar()).contains(SWIFT);
    }

    @Test
    void refusesADashboardFigureNoPetrolCarShowsForACarFromTheRegister() {
        assertThatThrownBy(() -> new Vehicle(SWIFT_FOUND, 45.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 2.0 and 30.0");
    }
}
