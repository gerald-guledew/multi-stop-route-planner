package com.solvelify.routeplanner.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PlanningPropertiesTest {

    @Test
    void countsAMetreClimbedAsTenMetresOfRoadUnlessToldOtherwise() {
        assertThat(new PlanningProperties(null).roadMetresPerMetreClimbed()).isEqualTo(10);
    }

    @Test
    void keepsWhatItWasSetTo() {
        assertThat(new PlanningProperties(25.0).roadMetresPerMetreClimbed()).isEqualTo(25);
        // Nought is allowed. The climb then counts for nothing.
        assertThat(new PlanningProperties(0.0).roadMetresPerMetreClimbed()).isZero();
    }

    @Test
    void refusesWhatIsNotANumberOfMetres() {
        for (double notMetres : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatThrownBy(() -> new PlanningProperties(notMetres))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("road-metres-per-metre-climbed");
        }
    }

    @Test
    void isReadFromTheSettingOfThatName() {
        new ApplicationContextRunner()
                .withUserConfiguration(Settings.class)
                .withPropertyValues("routeplanner.planning.road-metres-per-metre-climbed=25")
                .run(context -> assertThat(context.getBean(PlanningProperties.class).roadMetresPerMetreClimbed())
                        .isEqualTo(25));
    }

    @EnableConfigurationProperties(PlanningProperties.class)
    static class Settings {
    }
}
