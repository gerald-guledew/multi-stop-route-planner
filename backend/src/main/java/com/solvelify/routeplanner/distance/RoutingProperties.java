package com.solvelify.routeplanner.distance;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings that decide where distances come from.
 *
 * @param provider    {@code haversine} for straight lines, {@code graphhopper} for real roads
 * @param osmFile     the OpenStreetMap extract to route on, ignored by haversine
 * @param graphCache  where GraphHopper keeps the prepared graph between runs
 * @param profile     the GraphHopper profile name, {@code car} for now
 */
@ConfigurationProperties(prefix = "routeplanner.routing")
public record RoutingProperties(
        String provider,
        String osmFile,
        String graphCache,
        String profile) {

    public static final String GRAPHHOPPER = "graphhopper";
    public static final String HAVERSINE = "haversine";
}
