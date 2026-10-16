package com.solvelify.routeplanner.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
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
    void keepsTheDescentsOfBothDrivesInTheOrderDriven() {
        ClimbAndDescent there = new ClimbAndDescent(0, 30, List.of(new ClimbAndDescent.Descent(30, 600)));
        ClimbAndDescent back = new ClimbAndDescent(30, 20, List.of(new ClimbAndDescent.Descent(20, 2000)));

        assertThat(there.plus(back).descents())
                .containsExactly(new ClimbAndDescent.Descent(30, 600), new ClimbAndDescent.Descent(20, 2000));
    }

    @Test
    void refusesADescentThatDropsNothing() {
        assertThatThrownBy(() -> new ClimbAndDescent.Descent(0, 600)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClimbAndDescent.Descent(30, -1)).isInstanceOf(IllegalArgumentException.class);
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
