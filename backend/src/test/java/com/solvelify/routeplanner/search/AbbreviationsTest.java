package com.solvelify.routeplanner.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AbbreviationsTest {

    @Test
    void saysWhatAnAbbreviationStandsFor() {
        assertThat(Abbreviations.inFull("rd")).containsExactly("road");
        assertThat(Abbreviations.inFull("st")).containsExactly("saint");
        assertThat(Abbreviations.inFull("road")).isEmpty();
        assertThat(Abbreviations.inFull("queen")).isEmpty();
    }

    @Test
    void saysHowAWordInFullIsAbbreviated() {
        assertThat(Abbreviations.shortFor("road")).contains("rd");
        assertThat(Abbreviations.shortFor("saint")).contains("st");
        assertThat(Abbreviations.shortFor("rd")).isEmpty();
        assertThat(Abbreviations.shortFor("queen")).isEmpty();
    }

    @Test
    void knowsAWordEitherWay() {
        assertThat(Abbreviations.knows("mt")).isTrue();
        assertThat(Abbreviations.knows("mount")).isTrue();
        assertThat(Abbreviations.knows("street")).isTrue();
        assertThat(Abbreviations.knows("ave")).isFalse();
        assertThat(Abbreviations.knows("queen")).isFalse();
    }

    @Test
    void readsOnlyTheWordInFullBothWaysWhenItsAbbreviationIsItsBeginning() {
        // "ave" typed finds "Avenue" as any unfinished word does. "avenue" is nowhere in "Ave".
        assertThat(Abbreviations.shortFor("avenue")).contains("ave");
        assertThat(Abbreviations.inFull("ave")).isEmpty();
        assertThat(Abbreviations.shortFor("street")).contains("st");
        assertThat(Abbreviations.inFull("st")).doesNotContain("street");
    }

    @Test
    void keepsApartTheAbbreviationsThatBeginTheirWord() {
        Abbreviations.BEGINS_ITS_WORD.forEach((word, abbreviation) -> assertThat(word).startsWith(abbreviation));
    }

    @Test
    void holdsNoAbbreviationThatIsTheStartOfItsWord() {
        // Those are found already, the way any unfinished word is.
        Abbreviations.IN_FULL.forEach((abbreviation, words) ->
                assertThat(words).noneMatch(word -> word.startsWith(abbreviation)));
    }

    @Test
    void givesEveryWordInFullOneAbbreviation() {
        Abbreviations.IN_FULL.forEach((abbreviation, words) ->
                assertThat(words).allSatisfy(word ->
                        assertThat(Abbreviations.shortFor(word)).contains(abbreviation)));
    }
}
