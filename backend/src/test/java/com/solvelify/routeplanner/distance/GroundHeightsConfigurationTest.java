package com.solvelify.routeplanner.distance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GroundHeightsConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Settings.class, GroundHeightsConfiguration.class);

    @Test
    void knowsNoHeightsWithHeightsOff() {
        runner.run(context -> assertThat(context.getBean(GroundHeights.class)).isSameAs(GroundHeights.NONE));
    }

    @Test
    void readsTheHeightFilesWithHeightsOn() {
        runner.withPropertyValues("routeplanner.routing.heights.enabled=true", "routeplanner.routing.heights.files=.")
                .run(context -> assertThat(context.getBean(GroundHeights.class)).isInstanceOf(HeightFiles.class));
    }

    @Test
    void readsThemWithStraightLinesToo() {
        runner.withPropertyValues(
                        "routeplanner.routing.provider=haversine",
                        "routeplanner.routing.heights.enabled=true",
                        "routeplanner.routing.heights.files=.")
                .run(context -> assertThat(context.getBean(GroundHeights.class)).isInstanceOf(HeightFiles.class));
    }

    @EnableConfigurationProperties(RoutingProperties.class)
    static class Settings {
    }
}
