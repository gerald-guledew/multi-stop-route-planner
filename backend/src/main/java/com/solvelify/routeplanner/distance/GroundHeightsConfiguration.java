package com.solvelify.routeplanner.distance;

import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The height of the ground at a pin, read from the height files when heights are on. Whichever
 * way the distances are worked out: the files are on disk or they are not.
 */
@Configuration
class GroundHeightsConfiguration {

    @Bean
    GroundHeights groundHeights(RoutingProperties properties) {
        RoutingProperties.Heights heights = properties.heights();
        return heights.enabled() ? new HeightFiles(Path.of(heights.files())) : GroundHeights.NONE;
    }
}
