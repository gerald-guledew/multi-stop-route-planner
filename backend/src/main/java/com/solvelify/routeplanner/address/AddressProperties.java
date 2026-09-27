package com.solvelify.routeplanner.address;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param importEnabled run the one-off import on the next start
 * @param csv           path to the LINZ address export
 */
@ConfigurationProperties(prefix = "routeplanner.addresses")
public record AddressProperties(Boolean importEnabled, String csv) {

    public AddressProperties {
        if (csv == null || csv.isBlank()) {
            csv = "data/lds-nz-addresses-CSV/nz-addresses.csv";
        }
    }
}
