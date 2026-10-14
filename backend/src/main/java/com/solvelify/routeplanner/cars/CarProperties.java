package com.solvelify.routeplanner.cars;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param importEnabled run the import on the next start
 * @param csv           path to the file written by {@code scripts/export-nzta-cars.sh}
 */
@ConfigurationProperties(prefix = "routeplanner.cars")
public record CarProperties(Boolean importEnabled, String csv) {

    public CarProperties {
        if (csv == null || csv.isBlank()) {
            csv = "data/nzta-cars.csv";
        }
    }
}
