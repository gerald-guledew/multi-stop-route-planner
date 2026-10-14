package com.solvelify.routeplanner.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VehicleTest {

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
}
