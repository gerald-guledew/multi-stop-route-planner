package com.solvelify.routeplanner.search;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Words that addresses and names write two ways: in full, and as an abbreviation.
 *
 * <p>Neither source keeps to one way. LINZ writes Saint Heliers and Mount Eden in full, and
 * St Lukes Road and Mt Pleasant Road short. Overture writes most streets short, as in
 * "10 Clonbern Rd" and "42 Nuffield St". So a word is read both ways, whichever way it was
 * typed.
 *
 * <p>An abbreviation that is the beginning of its word needs no help when it is the one
 * typed. "ave" is found in "Avenue" the way any unfinished word is, and so is "st" in
 * "Street". The others do: "rd" is nowhere in "Road", so without this "12 bassett rd" finds
 * nothing, and "st heliers" misses everything in Saint Heliers.
 *
 * <p>A word typed in full always needs help, because it is nowhere in its abbreviation.
 * Without this "42 nuffield street" finds nothing either.
 */
final class Abbreviations {

    /** Abbreviations that are not the beginning of their word, and what each stands for. */
    static final Map<String, List<String>> IN_FULL = Map.of(
            "rd", List.of("road"),
            "st", List.of("saint"),
            "mt", List.of("mount"),
            "pt", List.of("point"),
            "ln", List.of("lane"),
            "tce", List.of("terrace"),
            "ct", List.of("court"),
            "pde", List.of("parade"),
            "hwy", List.of("highway"),
            "blvd", List.of("boulevard"));

    /** Words whose abbreviation is their beginning, so that only the word in full is helped. */
    static final Map<String, String> BEGINS_ITS_WORD = Map.of(
            "street", "st",
            "avenue", "ave",
            "drive", "dr",
            "place", "pl",
            "crescent", "cres");

    private static final Map<String, String> SHORT = shortForEachWord();

    private Abbreviations() {
    }

    /**
     * What an abbreviation is also read as. Nothing when the word is not an abbreviation, or
     * is one that begins its word. In lower case.
     */
    static List<String> inFull(String word) {
        return IN_FULL.getOrDefault(word, List.of());
    }

    /** The abbreviation that a word written in full is also read as. In lower case. */
    static Optional<String> shortFor(String word) {
        return Optional.ofNullable(SHORT.get(word));
    }

    /** Whether the word is one of these, short or in full. */
    static boolean knows(String word) {
        return IN_FULL.containsKey(word) || SHORT.containsKey(word);
    }

    private static Map<String, String> shortForEachWord() {
        Map<String, String> shortFor = new HashMap<>(BEGINS_ITS_WORD);
        IN_FULL.forEach((abbreviation, words) -> words.forEach(word -> shortFor.put(word, abbreviation)));
        return Map.copyOf(shortFor);
    }
}
