package com.solvelify.routeplanner.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ClimbAndDescentTest {

    @Test
    void addsOneDriveToTheNext() {
        ClimbAndDescent overTheHill = new ClimbAndDescent(80, 50);
        ClimbAndDescent downToTheSea = new ClimbAndDescent(5, 30);

        assertThat(overTheHill.plus(downToTheSea)).isEqualTo(new ClimbAndDescent(85, 80));
    }

    @Test
    void keepsTheWayUpApartFromTheWayDown() {
        // Over a hill and back to the same height. The two do not cancel out.
        ClimbAndDescent thereAndBack = new ClimbAndDescent(80, 50).plus(new ClimbAndDescent(50, 80));

        assertThat(thereAndBack.climbMetres()).isEqualTo(130);
        assertThat(thereAndBack.descentMetres()).isEqualTo(130);
    }

    @Test
    void isUnchangedByALevelDrive() {
        ClimbAndDescent overTheHill = new ClimbAndDescent(80, 50);

        assertThat(overTheHill.plus(ClimbAndDescent.LEVEL)).isEqualTo(overTheHill);
        assertThat(ClimbAndDescent.LEVEL.plus(overTheHill)).isEqualTo(overTheHill);
    }

    @Test
    void refusesADescentWrittenAsANegativeNumber() {
        assertThatThrownBy(() -> new ClimbAndDescent(80, -50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("-50");
    }

    @Test
    void refusesAClimbBelowZero() {
        assertThatThrownBy(() -> new ClimbAndDescent(-1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
