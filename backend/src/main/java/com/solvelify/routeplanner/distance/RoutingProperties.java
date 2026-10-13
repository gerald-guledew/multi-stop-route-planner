package com.solvelify.routeplanner.distance;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings that decide where distances come from.
 *
 * @param provider    {@code haversine} for straight lines, {@code graphhopper} for real roads
 * @param osmFile     the OpenStreetMap extract to route on, ignored by haversine
 * @param graphCache  where GraphHopper keeps the prepared graph between runs
 * @param profile     the GraphHopper profile name, {@code car} for now
 * @param heights     whether the roads are read with their heights, ignored by haversine
 */
@ConfigurationProperties(prefix = "routeplanner.routing")
public record RoutingProperties(
        String provider,
        String osmFile,
        String graphCache,
        String profile,
        @DefaultValue Heights heights) {

    public static final String GRAPHHOPPER = "graphhopper";
    public static final String HAVERSINE = "haversine";

    /**
     * How high the roads are, which is what lets a plan say how much a leg climbs.
     *
     * @param enabled     off unless asked for, because switching it on fetches height files
     * @param files       where the height files are kept once fetched
     * @param graphCache  where the graph prepared with heights is kept. A graph with heights and
     *                    one without cannot be read as each other, so each has its own folder
     */
    public record Heights(boolean enabled, String files, String graphCache) {
    }
}
