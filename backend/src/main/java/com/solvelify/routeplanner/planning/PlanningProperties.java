package com.solvelify.routeplanner.planning;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings that decide how an order is chosen.
 *
 * @param roadMetresPerMetreClimbed how many metres of level road a metre of climb counts as
 *                                  when an order is chosen for distance and climb together.
 *                                  At nought the climb counts for nothing
 */
@ConfigurationProperties(prefix = "routeplanner.planning")
public record PlanningProperties(Double roadMetresPerMetreClimbed) {

    public PlanningProperties {
        if (roadMetresPerMetreClimbed == null) {
            roadMetresPerMetreClimbed = 10.0;
        }
        if (!Double.isFinite(roadMetresPerMetreClimbed) || roadMetresPerMetreClimbed < 0) {
            throw new IllegalArgumentException(
                    "routeplanner.planning.road-metres-per-metre-climbed is " + roadMetresPerMetreClimbed
                            + ". It is a number of metres of road, nought or more");
        }
    }
}
