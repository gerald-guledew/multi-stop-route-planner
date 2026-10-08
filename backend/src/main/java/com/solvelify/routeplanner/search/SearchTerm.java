package com.solvelify.routeplanner.search;

import java.util.Optional;

/**
 * What somebody typed, tidied into the two forms the query needs.
 *
 * @param words   the search with commas and extra spaces removed, used to rank the matches
 * @param pattern the same words as a LIKE pattern, used to find them
 */
record SearchTerm(String words, String pattern) {

    /** Two characters match half the country and teach the user nothing. */
    static final int MINIMUM_LENGTH = 3;

    static Optional<SearchTerm> from(String typed) {
        if (typed == null) {
            return Optional.empty();
        }

        // A comma is how people separate the parts of an address, not something to look for.
        String words = typed.replace(',', ' ').strip().replaceAll("\\s+", " ");
        if (words.length() < MINIMUM_LENGTH) {
            return Optional.empty();
        }

        return Optional.of(new SearchTerm(words, asPattern(words)));
    }

    /**
     * Each space becomes a wildcard, so "bassett road remuera" is looked for as
     * {@code %bassett%road%remuera%}. A plain substring search fails here: the stored address
     * reads "20A Bassett Road, Remuera", and the comma alone is enough to stop it matching.
     *
     * <p>The trade-off is that words must be typed in the order they appear, which is how
     * people type addresses.
     */
    private static String asPattern(String words) {
        // LIKE gives % and _ a meaning of their own. Typed by a user they are just characters.
        String literal = words
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");

        return "%" + literal.replace(' ', '%') + "%";
    }
}
