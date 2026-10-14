package com.solvelify.routeplanner.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SearchTermTest {

    // Tidying.

    @Test
    void looksForTheWordsInOrderWithAnythingBetweenThem() {
        SearchTerm term = SearchTerm.from("bassett way remuera").orElseThrow();

        assertThat(term.anySpelling()).isEqualTo("bassett.*way.*remuera");
        assertThat(term.spellings())
                .containsExactly(new SearchTerm.Spelling("bassett way remuera", "bassett.*way.*remuera"));
    }

    @Test
    void treatsACommaAsASpace() {
        SearchTerm term = SearchTerm.from("Sylvia Park Shopping Centre, Auckland").orElseThrow();

        assertThat(words(term)).containsExactly("Sylvia Park Shopping Centre Auckland");
        assertThat(term.anySpelling()).isEqualTo("Sylvia.*Park.*Shopping.*Centre.*Auckland");
    }

    @Test
    void squeezesExtraSpacesOut() {
        assertThat(words(SearchTerm.from("  new    world  ").orElseThrow())).containsExactly("new world");
    }

    @Test
    void keepsWhatAPatternWouldReadAsAnOrdinaryCharacter() {
        assertThat(SearchTerm.from("a.b (c) d*e f+ g? [h] {i} j|k ^l $m").orElseThrow().anySpelling())
                .isEqualTo("a\\.b.*\\(c\\).*d\\*e.*f\\+.*g\\?.*\\[h\\].*\\{i\\}.*j\\|k.*\\^l.*\\$m");
    }

    @Test
    void keepsABackslashAsAnOrdinaryCharacter() {
        assertThat(SearchTerm.from("a\\b way").orElseThrow().anySpelling()).isEqualTo("a\\\\b.*way");
    }

    @Test
    void leavesAloneWhatMeansNothingToAPattern() {
        assertThat(SearchTerm.from("100% pure_nz 2/286 h&m").orElseThrow().anySpelling())
                .isEqualTo("100%.*pure_nz.*2/286.*h&m");
    }

    @Test
    void readsACharacterThatIsNeitherALetterNorPlainPunctuationAsASpace() {
        // The database would turn the first into an asterisk and the second into "(C)".
        assertThat(words(SearchTerm.from("bank × corner").orElseThrow())).containsExactly("bank corner");
        assertThat(words(SearchTerm.from("bank©corner").orElseThrow())).containsExactly("bank corner");
        assertThat(words(SearchTerm.from("bank\u0000corner\tnorth").orElseThrow())).containsExactly("bank corner north");
    }

    @Test
    void keepsLettersWithAccents() {
        assertThat(words(SearchTerm.from("Whangārei café").orElseThrow())).containsExactly("Whangārei café");
    }

    @Test
    void keepsACurledApostropheSoThatTheNameStaysOneWord() {
        assertThat(words(SearchTerm.from("O’Neill way").orElseThrow())).containsExactly("O’Neill way");
    }

    @Test
    void hasNothingToSearchForBelowThreeCharacters() {
        assertThat(SearchTerm.from("ba")).isEmpty();
        assertThat(SearchTerm.from("st")).isEmpty();
        assertThat(SearchTerm.from(" , ")).isEmpty();
        assertThat(SearchTerm.from(null)).isEmpty();
    }

    @Test
    void countsTheLengthAfterTidying() {
        // Six characters typed, two of them worth searching for.
        assertThat(SearchTerm.from(" b , a ")).isPresent();
        assertThat(SearchTerm.from(" b ,   ")).isEmpty();
    }

    // Words written two ways.

    @Test
    void readsAnAbbreviationAsTheWordItStandsFor() {
        SearchTerm term = SearchTerm.from("12 bassett rd").orElseThrow();

        assertThat(term.spellings()).containsExactly(
                new SearchTerm.Spelling("12 bassett rd", "12.*bassett.*rd"),
                new SearchTerm.Spelling("12 bassett road", "12.*bassett.*road"));
        assertThat(term.anySpelling()).isEqualTo("12.*bassett.*(rd|road)");
    }

    @Test
    void readsAWordInFullAsItsAbbreviationStandingAsAWordOfItsOwn() {
        SearchTerm term = SearchTerm.from("saint lukes").orElseThrow();

        assertThat(term.spellings()).containsExactly(
                new SearchTerm.Spelling("saint lukes", "saint.*lukes"),
                new SearchTerm.Spelling("st lukes", "(^| )st\\M.*lukes"));
        assertThat(term.anySpelling()).isEqualTo("(saint|(^| )st\\M).*lukes");
    }

    @Test
    void spellsEveryCombinationWithTheOneThatWasTypedFirst() {
        SearchTerm term = SearchTerm.from("Mt Eden Rd").orElseThrow();

        assertThat(words(term))
                .containsExactly("Mt Eden Rd", "Mt Eden road", "mount Eden Rd", "mount Eden road");
        assertThat(term.anySpelling()).isEqualTo("(Mt|mount).*Eden.*(Rd|road)");
    }

    @Test
    void readsAnAbbreviationThatBeginsItsWordOnlyFromTheWordInFull() {
        // "st" already finds "Street", being the start of it. "street" is nowhere in "St".
        assertThat(words(SearchTerm.from("queen st").orElseThrow())).containsExactly("queen st", "queen saint");

        SearchTerm inFull = SearchTerm.from("queen street").orElseThrow();
        assertThat(words(inFull)).containsExactly("queen street", "queen st");
        assertThat(inFull.anySpelling()).isEqualTo("queen.*(street|(^| )st\\M)");

        // "ave" is not read as anything but its letters.
        assertThat(words(SearchTerm.from("queen ave").orElseThrow())).containsExactly("queen ave");
        assertThat(words(SearchTerm.from("queen avenue").orElseThrow())).containsExactly("queen avenue", "queen ave");
    }

    @Test
    void leavesOutAFullStopAfterOneOfTheseWords() {
        SearchTerm term = SearchTerm.from("st. lukes rd.").orElseThrow();

        assertThat(term.anySpelling()).isEqualTo("(st|saint).*lukes.*(rd|road)");
        assertThat(words(term).getFirst()).isEqualTo("st. lukes rd.");
    }

    @Test
    void keepsAFullStopAfterAnyOtherWord() {
        assertThat(SearchTerm.from("st. lukes inc.").orElseThrow().anySpelling())
                .isEqualTo("(st|saint).*lukes.*inc\\.");
    }

    @Test
    void readsAWordInFullAsItsAbbreviationOnlyWhenSomethingElseNarrowsTheSearch() {
        // Alone, or beside a word too short for the index, it is looked for as typed.
        assertThat(SearchTerm.from("saint").orElseThrow().spellings()).hasSize(1);
        assertThat(SearchTerm.from("saint he").orElseThrow().spellings()).hasSize(1);
        assertThat(SearchTerm.from("12 road").orElseThrow().spellings()).hasSize(1);

        assertThat(SearchTerm.from("saint hel").orElseThrow().spellings()).hasSize(2);
        assertThat(SearchTerm.from("12a road").orElseThrow().spellings()).hasSize(2);
    }

    @Test
    void doesNotCountAnotherOfTheseWordsAsNarrowingTheSearch() {
        assertThat(words(SearchTerm.from("mount road").orElseThrow())).containsExactly("mount road");
    }

    @Test
    void readsAnAbbreviationInFullEvenWhenNothingElseWasTyped() {
        assertThat(words(SearchTerm.from("mt e").orElseThrow())).containsExactly("mt e", "mount e");
        assertThat(words(SearchTerm.from("hwy").orElseThrow())).containsExactly("hwy", "highway");
    }

    @Test
    void stopsAddingSpellingsPastTheMostItAllows() {
        SearchTerm term = SearchTerm.from("mt st pt rd lukes").orElseThrow();

        // Three words doubled make eight. The fourth is looked for as typed.
        assertThat(term.spellings()).hasSize(SearchTerm.MOST_SPELLINGS);
        assertThat(term.anySpelling()).isEqualTo("(mt|mount).*(st|saint).*(pt|point).*rd.*lukes");
        assertThat(words(term)).allMatch(spelling -> spelling.endsWith(" rd lukes"));
    }

    // The first word, as a name would hold it.

    @Test
    void looksForTheFirstWordWhereAWordBegins() {
        assertThat(SearchTerm.from("mobil st heliers").orElseThrow().firstWord()).isEqualTo("\\mmobil");
        assertThat(SearchTerm.from("a.b way").orElseThrow().firstWord()).isEqualTo("\\ma\\.b");
    }

    @Test
    void looksForAFirstWordThatBeginsWithPunctuationAnywhere() {
        assertThat(SearchTerm.from("(the) warehouse").orElseThrow().firstWord()).isEqualTo("\\(the\\)");
    }

    @Test
    void looksForAnAbbreviationInANameAsAWholeWordOrAsTheWordItStandsFor() {
        assertThat(SearchTerm.from("st johns").orElseThrow().firstWord()).isEqualTo("\\m(st\\M|saint)");
        assertThat(SearchTerm.from("St. Johns").orElseThrow().firstWord()).isEqualTo("\\m(St\\M|saint)");
    }

    @Test
    void looksForAWordInFullInANameAsThatOrAsItsAbbreviation() {
        assertThat(SearchTerm.from("mount eden village").orElseThrow().firstWord()).isEqualTo("\\m(mount|mt\\M)");
        assertThat(SearchTerm.from("mount").orElseThrow().firstWord()).isEqualTo("\\mmount");
    }

    // What the database is trusted not to turn into part of a pattern.

    @Test
    void looksForLettersDigitsAndPlainPunctuation() {
        assertThat(SearchTerm.isLookedFor('a')).isTrue();
        assertThat(SearchTerm.isLookedFor('7')).isTrue();
        assertThat(SearchTerm.isLookedFor('ā')).isTrue();
        assertThat(SearchTerm.isLookedFor('/')).isTrue();
        assertThat(SearchTerm.isLookedFor('\'')).isTrue();
        assertThat(SearchTerm.isLookedFor('’')).isTrue();
    }

    @Test
    void readsEverythingElseAsASpace() {
        assertThat(SearchTerm.isLookedFor(',')).isFalse();
        assertThat(SearchTerm.isLookedFor(' ')).isFalse();
        assertThat(SearchTerm.isLookedFor('\t')).isFalse();
        assertThat(SearchTerm.isLookedFor(0)).isFalse();
        assertThat(SearchTerm.isLookedFor(127)).isFalse();
        assertThat(SearchTerm.isLookedFor('×')).isFalse();
        assertThat(SearchTerm.isLookedFor('…')).isFalse();
        // A letter by its Unicode category, and the database makes a caret of it.
        assertThat(SearchTerm.isLookedFor('ˆ')).isFalse();
        assertThat(SearchTerm.isLookedFor(0x1F600)).isFalse();
    }

    private static List<String> words(SearchTerm term) {
        return term.spellings().stream().map(SearchTerm.Spelling::words).toList();
    }
}
