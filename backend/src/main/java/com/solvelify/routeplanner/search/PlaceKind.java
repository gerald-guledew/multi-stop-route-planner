package com.solvelify.routeplanner.search;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** What a place is, for finding a stop when there is no name to type. */
public enum PlaceKind {

    FUEL,
    FOOD,
    CAFE,
    TOILETS,
    DAIRY,
    GROCERIES,
    BEACH,
    PARK,
    LOOKOUT,
    TOURIST,
    ZOO,
    MALL;

    /** As the database and the API write it. */
    public String written() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Empty for a word that is no kind of place. */
    public static Optional<PlaceKind> called(String written) {
        return Arrays.stream(values()).filter(kind -> kind.written().equals(written)).findFirst();
    }
}
