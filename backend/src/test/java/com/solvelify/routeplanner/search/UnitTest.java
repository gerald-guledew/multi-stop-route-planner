package com.solvelify.routeplanner.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class UnitTest {

    @Test
    void looksForTheBuildingWhenAUnitWasTypedBeforeASlash() {
        assertThat(Unit.otherWaysToFind("5/100 Broadway")).containsExactly("100 Broadway");
        assertThat(Unit.otherWaysToFind("3/40A Smith Street, Newmarket"))
                .containsExactly("40A Smith Street Newmarket");
    }

    @Test
    void takesAUnitOfLettersAndDigitsAsLinzWritesThem() {
        assertThat(Unit.otherWaysToFind("G04/323 Victoria Avenue")).containsExactly("323 Victoria Avenue");
        assertThat(Unit.otherWaysToFind("7A/256 Blind Bay Road")).containsExactly("256 Blind Bay Road");
        assertThat(Unit.otherWaysToFind("E/57A Spring Street")).containsExactly("57A Spring Street");
        assertThat(Unit.otherWaysToFind("W101A/28 Torrens Terrace")).containsExactly("28 Torrens Terrace");
    }

    @Test
    void writesAUnitTypedInWordsTheWayLinzDoesAndThenLooksForTheBuilding() {
        assertThat(Unit.otherWaysToFind("Unit 5, 100 Broadway"))
                .containsExactly("5/100 Broadway", "100 Broadway");
        assertThat(Unit.otherWaysToFind("flat b 12 smith street"))
                .containsExactly("b/12 smith street", "12 smith street");
    }

    @Test
    void knowsTheWordsPeoplePutBeforeAUnit() {
        for (String word : List.of("unit", "flat", "apartment", "apt", "shop", "suite", "villa")) {
            assertThat(Unit.otherWaysToFind(word + " 5 100 broadway")).as(word)
                    .containsExactly("5/100 broadway", "100 broadway");
        }
    }

    @Test
    void readsTheWordWhateverItsCapitalsAndWithAFullStop() {
        assertThat(Unit.otherWaysToFind("APT. 5 100 Broadway"))
                .containsExactly("5/100 Broadway", "100 Broadway");
    }

    @Test
    void takesAUnitInWordsThatAlreadyHasItsSlash() {
        assertThat(Unit.otherWaysToFind("Unit 5/100 Broadway"))
                .containsExactly("5/100 Broadway", "100 Broadway");
    }

    @Test
    void hasNothingElseToLookForWhenNoUnitWasTyped() {
        assertThat(Unit.otherWaysToFind("100 Broadway")).isEmpty();
        assertThat(Unit.otherWaysToFind("new world")).isEmpty();
        assertThat(Unit.otherWaysToFind("")).isEmpty();
        assertThat(Unit.otherWaysToFind("  , ")).isEmpty();
        assertThat(Unit.otherWaysToFind(null)).isEmpty();
    }

    @Test
    void doesNotTakeAWordForAUnit() {
        // Flat Bush is a suburb. "flat" there says nothing about a flat.
        assertThat(Unit.otherWaysToFind("flat bush school road")).isEmpty();
        assertThat(Unit.otherWaysToFind("unit")).isEmpty();
        assertThat(Unit.otherWaysToFind("north/south road")).isEmpty();
        // Nor when a number comes after it.
        assertThat(Unit.otherWaysToFind("flat bush 12 smith street")).isEmpty();
        assertThat(Unit.otherWaysToFind("north/12 smith street")).isEmpty();
    }

    @Test
    void leavesANumberAfterTheWordAloneWhenNoStreetNumberFollowsIt() {
        // The 5 may be the street number, with nobody having said which unit.
        assertThat(Unit.otherWaysToFind("unit 5 queen street")).isEmpty();
        assertThat(Unit.otherWaysToFind("unit 5")).isEmpty();
    }

    @Test
    void needsAStreetNumberAfterTheSlash() {
        assertThat(Unit.otherWaysToFind("a/b testing")).isEmpty();
        assertThat(Unit.otherWaysToFind("5/")).isEmpty();
        assertThat(Unit.otherWaysToFind("/100 Broadway")).isEmpty();
    }

    @Test
    void takesAUnitOfSixCharactersAndNothingLonger() {
        assertThat(Unit.otherWaysToFind("W101AB/28 Torrens Terrace")).containsExactly("28 Torrens Terrace");
        assertThat(Unit.otherWaysToFind("1234567/100 Broadway")).isEmpty();
    }

    @Test
    void dropsOnlyWhatComesBeforeTheFirstSlash() {
        assertThat(Unit.otherWaysToFind("5/100/2 Broadway")).containsExactly("100/2 Broadway");
    }

    @Test
    void ignoresSpacesAndCommasAroundWhatWasTyped() {
        assertThat(Unit.otherWaysToFind("  5/100   Broadway ,")).containsExactly("100 Broadway");
    }
}
