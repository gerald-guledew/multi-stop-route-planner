package com.solvelify.routeplanner.poi;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param importEnabled run the import on the next start
 * @param csv           path to the file written by {@code scripts/export-overture-places.sql}
 * @param osmCsv        path to the file written by {@code scripts/export-osm-places.sql}
 */
@ConfigurationProperties(prefix = "routeplanner.poi")
public record PoiProperties(Boolean importEnabled, String csv, String osmCsv) {

    public PoiProperties {
        if (csv == null || csv.isBlank()) {
            csv = "data/overture-nz-places.csv";
        }
        if (osmCsv == null || osmCsv.isBlank()) {
            osmCsv = "data/osm-nz-places.csv";
        }
    }
}
