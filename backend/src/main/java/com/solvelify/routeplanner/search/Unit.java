package com.solvelify.routeplanner.search;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A flat, shop or apartment typed in front of a street number, and what else to look for when
 * the address as typed finds nothing.
 *
 * <p>LINZ writes every unit one way, before a slash: "5/100 Broadway". It lists the units of
 * some buildings and not of others, and a building whose units are not listed is there under
 * its street number alone. So an address with a unit can fail for two reasons. The unit was
 * written another way, "Unit 5, 100 Broadway". Or the unit is not listed, though the building
 * is.
 *
 * <p>None of this is tried while the search as typed finds something. A place that holds what
 * was typed is a better answer than one that holds part of it.
 */
final class Unit {

    /** Words people put before a unit. LINZ uses none of them. */
    private static final Set<String> SAYS_A_UNIT_FOLLOWS =
            Set.of("unit", "flat", "apartment", "apt", "shop", "suite", "villa");

    /** The longest unit in the LINZ data is five characters, as in "W101A". This leaves room. */
    private static final int LONGEST_UNIT = 6;

    private Unit() {
    }

    /**
     * What else to search for, in the order to try it. A unit typed in words comes first as
     * LINZ would write it. Then the building, without its unit. Empty when what was typed does
     * not begin with a unit and the street number it belongs to.
     */
    static List<String> otherWaysToFind(String typed) {
        if (typed == null) {
            return List.of();
        }
        // A comma counts as a space, as it does for the search itself.
        List<String> words = Arrays.stream(typed.split("[\\s,]+")).filter(word -> !word.isEmpty()).toList();
        if (words.isEmpty()) {
            return List.of();
        }

        if (saysAUnitFollows(words.getFirst())) {
            return fromAUnitInWords(words.subList(1, words.size()));
        }
        if (isAUnitBeforeAStreetNumber(words.getFirst())) {
            return List.of(afterTheSlash(String.join(" ", words)));
        }
        return List.of();
    }

    /** What follows the word: "5 100 Broadway", or "5/100 Broadway". */
    private static List<String> fromAUnitInWords(List<String> afterTheWord) {
        if (afterTheWord.isEmpty()) {
            return List.of();
        }
        if (isAUnitBeforeAStreetNumber(afterTheWord.getFirst())) {
            String asLinzWritesIt = String.join(" ", afterTheWord);
            return List.of(asLinzWritesIt, afterTheSlash(asLinzWritesIt));
        }
        // Without a street number after it, "5" in "unit 5 queen street" may be the number.
        if (afterTheWord.size() < 2 || !isAUnit(afterTheWord.get(0)) || !beginsWithADigit(afterTheWord.get(1))) {
            return List.of();
        }
        String building = String.join(" ", afterTheWord.subList(1, afterTheWord.size()));
        return List.of(afterTheWord.get(0) + "/" + building, building);
    }

    private static boolean saysAUnitFollows(String word) {
        String bare = word.endsWith(".") ? word.substring(0, word.length() - 1) : word;
        return SAYS_A_UNIT_FOLLOWS.contains(bare.toLowerCase(Locale.ROOT));
    }

    /** "5/100" and "G04/100A". Not "a/b", and not a slash with nothing after it. */
    private static boolean isAUnitBeforeAStreetNumber(String word) {
        int slash = word.indexOf('/');
        return slash > 0 && isAUnit(word.substring(0, slash)) && beginsWithADigit(word.substring(slash + 1));
    }

    /**
     * Letters and digits with a digit among them, or one letter: "5", "7A", "G04", "E". A whole
     * word is not a unit, which keeps "flat bush road" from being read as flat "bush".
     */
    private static boolean isAUnit(String text) {
        if (text.isEmpty() || text.length() > LONGEST_UNIT || !text.chars().allMatch(Character::isLetterOrDigit)) {
            return false;
        }
        return text.length() == 1 || text.chars().anyMatch(Character::isDigit);
    }

    private static boolean beginsWithADigit(String text) {
        return !text.isEmpty() && Character.isDigit(text.charAt(0));
    }

    private static String afterTheSlash(String text) {
        return text.substring(text.indexOf('/') + 1);
    }
}
