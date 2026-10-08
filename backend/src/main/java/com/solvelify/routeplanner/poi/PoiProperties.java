package com.solvelify.routeplanner.poi;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param importEnabled run the import on the next start
 * @param csv           path to the file written by {@code scripts/export-overture-places.sql}
 */
@ConfigurationProperties(prefix = "routeplanner.poi")
public record PoiProperties(Boolean importEnabled, String csv) {

    public PoiProperties {
        if (csv == null || csv.isBlank()) {
            csv = "data/overture-nz-places.csv";
        }
    }
}
