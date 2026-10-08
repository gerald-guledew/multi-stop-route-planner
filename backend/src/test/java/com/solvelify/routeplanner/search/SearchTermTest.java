package com.solvelify.routeplanner.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchTermTest {

    @Test
    void turnsEachSpaceIntoAWildcard() {
        assertThat(SearchTerm.from("bassett road remuera").orElseThrow().pattern())
                .isEqualTo("%bassett%road%remuera%");
    }

    @Test
    void treatsACommaAsASpace() {
        SearchTerm term = SearchTerm.from("90A Bassett Road, Remuera").orElseThrow();

        assertThat(term.words()).isEqualTo("90A Bassett Road Remuera");
        assertThat(term.pattern()).isEqualTo("%90A%Bassett%Road%Remuera%");
    }

    @Test
    void squeezesExtraSpacesOut() {
        assertThat(SearchTerm.from("  new    world  ").orElseThrow().words()).isEqualTo("new world");
    }

    @Test
    void keepsWhatLikeWouldTreatAsAWildcardAsAnOrdinaryCharacter() {
        assertThat(SearchTerm.from("100% pure_nz").orElseThrow().pattern())
                .isEqualTo("%100\\%%pure\\_nz%");
    }

    @Test
    void keepsABackslashAsAnOrdinaryCharacter() {
        assertThat(SearchTerm.from("a\\b street").orElseThrow().pattern()).isEqualTo("%a\\\\b%street%");
    }

    @Test
    void hasNothingToSearchForBelowThreeCharacters() {
        assertThat(SearchTerm.from("ba")).isEmpty();
        assertThat(SearchTerm.from(" , ")).isEmpty();
        assertThat(SearchTerm.from(null)).isEmpty();
    }

    @Test
    void countsTheLengthAfterTidying() {
        // Six characters typed, two of them worth searching for.
        assertThat(SearchTerm.from(" b , a ")).isPresent();
        assertThat(SearchTerm.from(" b ,   ")).isEmpty();
    }
}
