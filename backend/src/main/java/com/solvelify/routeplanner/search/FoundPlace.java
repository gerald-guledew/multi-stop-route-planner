package com.solvelify.routeplanner.search;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One search result.
 *
 * @param name   what to call the place: the full address, or the name of the business
 * @param detail a second line that tells two places with the same name apart, such as
 *               "10 Clonbern Rd, Remuera, Auckland". Null for an address, whose name
 *               already says where it is
 */
public record FoundPlace(Kind kind, String name, String detail, double latitude, double longitude) {

    public enum Kind {
        ADDRESS,
        /** A point of interest: a shop, a school, a hospital, anything known by its name. */
        POI
    }

    public static FoundPlace address(String fullAddress, double latitude, double longitude) {
        return new FoundPlace(Kind.ADDRESS, fullAddress, null, latitude, longitude);
    }

    public static FoundPlace poi(String name, String address, String suburb, String locality,
                                 double latitude, double longitude) {
        return new FoundPlace(Kind.POI, name, whereabouts(address, suburb, locality), latitude, longitude);
    }

    /**
     * Joins the parts that are present, leaving out any that an earlier part already says.
     * The data repeats itself often: an address of "Shop 2, 309 Broadway Newmarket" in the
     * suburb of Newmarket, the suburb Auckland Central in the city of Auckland, or the suburb
     * Whakatāne from one source beside the city Whakatane from the other.
     */
    static String whereabouts(String... parts) {
        List<String> said = new ArrayList<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String wanted = plain(part);
            boolean alreadySaid = said.stream().anyMatch(earlier -> plain(earlier).contains(wanted));
            if (!alreadySaid) {
                said.add(part.strip());
            }
        }
        return said.isEmpty() ? null : String.join(", ", said);
    }

    /** Lower case with accents removed, for comparing only. What is shown keeps its macrons. */
    private static String plain(String text) {
        return Normalizer.normalize(text.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
