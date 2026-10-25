package com.solvelify.routeplanner.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * What somebody typed, tidied, in every way of writing it that the search accepts.
 *
 * <p>"12 mt eden rd" is also "12 mount eden road", and both of the two in between. Each is a
 * spelling. A place is found when it matches any of them.
 *
 * @param spellings   the ways of writing what was typed, the way it was typed first
 * @param anySpelling one pattern that matches whatever any of the spellings matches
 * @param firstWord   a pattern that matches the first word where a word of a name begins,
 *                    in any way of writing it. A place whose name holds it is one the search
 *                    began to name
 */
record SearchTerm(List<Spelling> spellings, String anySpelling, String firstWord) {

    /** Two characters match half the country and teach the user nothing. */
    static final int MINIMUM_LENGTH = 3;

    /**
     * Every word with a second way of writing it doubles the spellings, and a place is checked
     * against each one. Eight allows three such words, as in "mt st john rd". Past that, the
     * words further along are looked for as typed.
     */
    static final int MOST_SPELLINGS = 8;

    private static final int CURLED_APOSTROPHE = '\u2019';
    private static final Pattern MEANS_SOMETHING_IN_A_PATTERN = Pattern.compile("[\\\\^$.|?*+()\\[\\]{}]");
    private static final Pattern THREE_LETTERS_OR_DIGITS = Pattern.compile("[\\p{L}\\p{Nd}]{3}");
    private static final Pattern LETTERS_OR_DIGITS = Pattern.compile("[\\p{L}\\p{Nd}]+");
    private static final String ANYTHING_BUT_LETTERS_OR_DIGITS = "[^[:alnum:]]*";

    /**
     * One way of writing what was typed.
     *
     * @param words   the words, which a match is ranked on
     * @param pattern a regular expression that finds them: the words in order, with anything
     *                in between. A plain substring search fails here, because the stored
     *                address reads "20A Bassett Road, Remuera" and the comma alone is enough
     *                to stop "bassett road remuera" matching. The trade-off is that words must
     *                be typed in the order they appear, which is how people type addresses
     * @param name    a regular expression that matches a name made of these words and nothing
     *                else, with or without spaces or punctuation between them, and the last one
     *                perhaps unfinished. So "pak n save" is the whole of "PAK'nSAVE", and "the
     *                wareh" the whole of "The Warehouse" though not of "The Rug Warehouse"
     */
    record Spelling(String words, String pattern, String name) {
    }

    /**
     * One way of writing one word.
     *
     * @param word    the word, which a match is ranked on
     * @param pattern what finds it in everything a place is searched by
     * @param inAName what finds it in a name, from where a word begins
     */
    private record Way(String word, String pattern, String inAName) {

        /** Looked for as letters anywhere, the way a word is that may not be finished. */
        static Way asLetters(String word, String letters) {
            return new Way(word, letters, letters);
        }

        /**
         * An abbreviation the way it was typed. It finds a place as letters anywhere, as every
         * word does. In a name it has to be the whole word: whoever typed "st johns" had
         * finished the "st", and did not mean a studio.
         */
        static Way anAbbreviationAsTyped(String word, String letters) {
            return new Way(word, letters, letters + "\\M");
        }

        /**
         * An abbreviation looked for on behalf of the word typed in full: after a space or at
         * the very start, and ending where the word ends. The space is spelt out because that
         * is what lets the index narrow the search. It is told that a word begins here.
         */
        static Way anAbbreviationOf(String abbreviation) {
            return new Way(abbreviation, "(^| )" + abbreviation + "\\M", abbreviation + "\\M");
        }
    }

    static Optional<SearchTerm> from(String typed) {
        if (typed == null) {
            return Optional.empty();
        }

        String tidied = tidied(typed);
        if (tidied.length() < MINIMUM_LENGTH) {
            return Optional.empty();
        }

        List<String> words = List.of(tidied.split(" "));
        boolean somethingElseToFindItBy = words.stream().anyMatch(SearchTerm::narrowsTheSearch);

        List<List<Way>> waysOfEachWord = new ArrayList<>();
        int spellings = 1;
        for (String word : words) {
            List<Way> ways = waysOfWriting(word, somethingElseToFindItBy);
            if (spellings * ways.size() > MOST_SPELLINGS) {
                ways = ways.subList(0, 1);
            }
            spellings *= ways.size();
            waysOfEachWord.add(ways);
        }

        return Optional.of(new SearchTerm(
                everyCombinationOf(waysOfEachWord),
                waysOfEachWord.stream().map(SearchTerm::anyOf).collect(Collectors.joining(".*")),
                inAName(waysOfEachWord.getFirst())));
    }

    /**
     * Whether a character is looked for, rather than read as a space.
     *
     * <p>A comma is how people separate the parts of an address, not something to look for.
     *
     * <p>The rest is about what the database does to the text before comparing it. Its
     * {@code searchable} function drops accents, and in doing so rewrites some symbols as
     * characters that mean something in a pattern: a multiplication sign becomes an asterisk,
     * a copyright sign becomes a letter in brackets. Letters and digits never do, apart from
     * the modifier letters, so those and plain punctuation are what is let through. The
     * curled apostrophe is too, because {@code searchable} deletes it, and a phone keyboard
     * puts one in "O'Neill", which must stay one word.
     */
    static boolean isLookedFor(int character) {
        if (character == ',') {
            return false;
        }
        if (character < 128) {
            return character > ' ' && character != 127;
        }
        if (character == CURLED_APOSTROPHE) {
            return true;
        }
        return Character.isLetterOrDigit(character) && Character.getType(character) != Character.MODIFIER_LETTER;
    }

    private static String tidied(String typed) {
        StringBuilder tidied = new StringBuilder(typed.length());
        typed.codePoints().forEach(character -> tidied.appendCodePoint(isLookedFor(character) ? character : ' '));
        return tidied.toString().strip().replaceAll("\\s+", " ");
    }

    /**
     * The ways one typed word may be written in an address or a name.
     *
     * <p>An abbreviation is looked for as typed, and as the word it stands for. As typed means
     * as letters anywhere, the way every word is looked for, so "st" still finds "Street", and
     * "Stanley" while somebody is on the way to typing it.
     *
     * <p>A word typed in full is also looked for as its abbreviation, standing as a word of
     * its own: "saint lukes" finds St Lukes Road and not Stanley Street. That is done only
     * when something else was typed that narrows the search. The index finds runs of three
     * characters, which "st" has none of, so on its own "saint" would have every address on
     * a street read through to find the few that say "St".
     *
     * <p>A full stop after one of these words is left out, so "st. lukes" finds St Lukes.
     */
    private static List<Way> waysOfWriting(String word, boolean somethingElseToFindItBy) {
        String bare = withoutItsFullStop(word);
        String known = bare.toLowerCase(Locale.ROOT);
        if (!Abbreviations.knows(known)) {
            return List.of(Way.asLetters(word, literally(word)));
        }

        List<String> inFull = Abbreviations.inFull(known);
        List<Way> ways = new ArrayList<>();
        ways.add(inFull.isEmpty()
                ? Way.asLetters(word, literally(bare))
                : Way.anAbbreviationAsTyped(word, literally(bare)));
        for (String wordInFull : inFull) {
            ways.add(Way.asLetters(wordInFull, wordInFull));
        }
        if (somethingElseToFindItBy) {
            Abbreviations.shortFor(known).ifPresent(abbreviation -> ways.add(Way.anAbbreviationOf(abbreviation)));
        }
        return ways;
    }

    /** A word the index can find, that is not itself one of the words written two ways. */
    private static boolean narrowsTheSearch(String word) {
        return !Abbreviations.knows(withoutItsFullStop(word).toLowerCase(Locale.ROOT))
                && THREE_LETTERS_OR_DIGITS.matcher(word).find();
    }

    private static String withoutItsFullStop(String word) {
        return word.endsWith(".") ? word.substring(0, word.length() - 1) : word;
    }

    /** To a regular expression these characters mean something. Typed by a user they are just characters. */
    private static String literally(String text) {
        return MEANS_SOMETHING_IN_A_PATTERN.matcher(text).replaceAll("\\\\$0");
    }

    /** The first combination takes the first way of every word, which is how it was typed. */
    private static List<Spelling> everyCombinationOf(List<List<Way>> waysOfEachWord) {
        List<List<Way>> combinations = List.of(List.of());
        for (List<Way> ways : waysOfEachWord) {
            List<List<Way>> longer = new ArrayList<>();
            for (List<Way> soFar : combinations) {
                for (Way way : ways) {
                    List<Way> combination = new ArrayList<>(soFar);
                    combination.add(way);
                    longer.add(combination);
                }
            }
            combinations = longer;
        }

        return combinations.stream()
                .map(combination -> new Spelling(
                        combination.stream().map(Way::word).collect(Collectors.joining(" ")),
                        combination.stream().map(Way::pattern).collect(Collectors.joining(".*")),
                        asAWholeName(combination)))
                .toList();
    }

    /**
     * Only the letters and digits are kept, so nothing in the pattern needs escaping. A
     * spelling with none at all is the whole of no name.
     */
    private static String asAWholeName(List<Way> combination) {
        List<String> runs = combination.stream()
                .flatMap(way -> LETTERS_OR_DIGITS.matcher(way.word()).results().map(MatchResult::group))
                .toList();
        if (runs.isEmpty()) {
            return "^$";
        }
        return "^" + String.join(ANYTHING_BUT_LETTERS_OR_DIGITS, runs)
                + "[[:alnum:]]*" + ANYTHING_BUT_LETTERS_OR_DIGITS + "$";
    }

    private static String anyOf(List<Way> ways) {
        return oneOf(ways.stream().map(Way::pattern).toList());
    }

    private static String oneOf(List<String> patterns) {
        return patterns.size() == 1 ? patterns.getFirst() : patterns.stream().collect(Collectors.joining("|", "(", ")"));
    }

    /**
     * The first word typed, in any way of writing it, where a word of a name begins. A
     * pattern can only say "a word begins here" before a letter or a digit, so a word typed
     * with something else in front is looked for anywhere.
     */
    private static String inAName(List<Way> waysOfTheFirstWord) {
        String any = oneOf(waysOfTheFirstWord.stream().map(Way::inAName).toList());
        boolean beginsLikeAWord = Character.isLetterOrDigit(waysOfTheFirstWord.getFirst().word().codePointAt(0));
        return beginsLikeAWord ? "\\m" + any : any;
    }
}
