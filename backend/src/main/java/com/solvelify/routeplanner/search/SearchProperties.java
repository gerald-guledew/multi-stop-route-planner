package com.solvelify.routeplanner.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param minConfidence  named places the source is less sure of than this are left out.
 *                       Overture scores every place from 0 to 1 for how likely it is to exist
 * @param candidateLimit the most matches from each table that are ranked for one search
 * @param scanLimit      when a search says where it is looking from, the most matches from
 *                       each table that are measured for distance. The nearest
 *                       {@code candidateLimit} of them are the ones ranked
 */
@ConfigurationProperties(prefix = "routeplanner.search")
public record SearchProperties(Double minConfidence, Integer candidateLimit, Integer scanLimit) {

    public SearchProperties {
        if (minConfidence == null) {
            minConfidence = 0.3;
        }
        if (candidateLimit == null) {
            candidateLimit = 5000;
        }
        if (scanLimit == null) {
            scanLimit = 20000;
        }
    }
}
