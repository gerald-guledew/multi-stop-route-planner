package com.solvelify.routeplanner.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NearbyAddressTest {

    @Test
    void callsASpotByItsStreetAddressAndSuburb() {
        assertThat(NearbyAddress.calledFrom("21 Clonbern Road, Remuera, Auckland"))
                .isEqualTo("21 Clonbern Road, Remuera");
    }

    @Test
    void leavesOutTheFlat() {
        assertThat(NearbyAddress.calledFrom("4C19/118 Broadway, Newmarket, Auckland"))
                .isEqualTo("118 Broadway, Newmarket");
    }

    @Test
    void keepsTheTownWhereThereIsNoSuburb() {
        assertThat(NearbyAddress.calledFrom("12 Revell Street, Hokitika"))
                .isEqualTo("12 Revell Street, Hokitika");
    }

    @Test
    void keepsAnAddressWithNothingAfterTheStreet() {
        assertThat(NearbyAddress.calledFrom("7/12 Revell Street")).isEqualTo("12 Revell Street");
    }
}
