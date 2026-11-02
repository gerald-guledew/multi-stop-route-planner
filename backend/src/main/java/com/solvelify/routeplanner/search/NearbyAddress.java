package com.solvelify.routeplanner.search;

/**
 * The address nearest a spot, to say where a pin stands.
 *
 * @param name   what to call the spot: the street address and its suburb, as {@link #calledFrom}
 *               gives them
 * @param metres how far the spot is from the address, in a straight line
 */
public record NearbyAddress(String name, double metres) {

    /**
     * What to call a spot from the address beside it, "118 Broadway, Newmarket" for "4C19/118
     * Broadway, Newmarket, Auckland". A pin stands at a building, and which of its flats is nearest
     * says nothing. LINZ writes a flat before a slash in the first word, and a suburb after the
     * first comma, or a town where there is no suburb. What comes after that is said by the suburb.
     */
    static String calledFrom(String fullAddress) {
        String[] parts = fullAddress.split(",\\s*");
        String street = parts[0].substring(parts[0].indexOf('/') + 1);
        return parts.length > 1 ? street + ", " + parts[1] : street;
    }
}
