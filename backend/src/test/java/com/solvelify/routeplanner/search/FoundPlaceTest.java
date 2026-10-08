package com.solvelify.routeplanner.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FoundPlaceTest {

    @Test
    void describesWhereANamedPlaceIs() {
        FoundPlace place = FoundPlace.poi("New World Remuera", "10 Clonbern Rd", "Remuera", "Auckland", -36.88, 174.79);

        assertThat(place.detail()).isEqualTo("10 Clonbern Rd, Remuera, Auckland");
    }

    @Test
    void doesNotRepeatASuburbTheAddressAlreadyNames() {
        assertThat(FoundPlace.whereabouts("Shop 2, 309 Broadway Newmarket", "Newmarket", "Auckland"))
                .isEqualTo("Shop 2, 309 Broadway Newmarket, Auckland");
    }

    @Test
    void doesNotRepeatACityTheSuburbAlreadyNames() {
        assertThat(FoundPlace.whereabouts("2 High St", "Auckland Central", "Auckland"))
                .isEqualTo("2 High St, Auckland Central");
    }

    @Test
    void doesNotRepeatAPlaceNameWrittenWithAndWithoutItsMacron() {
        // LINZ writes Whakatāne. Overture writes Whakatane. The first one said is kept.
        assertThat(FoundPlace.whereabouts("111 The Strand", "Whakatāne", "Whakatane"))
                .isEqualTo("111 The Strand, Whakatāne");
    }

    @Test
    void skipsWhateverIsMissing() {
        assertThat(FoundPlace.whereabouts(null, "Grafton", " ")).isEqualTo("Grafton");
    }

    @Test
    void hasNoDetailWhenNothingIsKnown() {
        assertThat(FoundPlace.whereabouts(null, null, null)).isNull();
    }

    @Test
    void givesAnAddressNoDetail() {
        FoundPlace place = FoundPlace.address("90A Bassett Road, Remuera, Auckland", -36.87, 174.78);

        assertThat(place.kind()).isEqualTo(FoundPlace.Kind.ADDRESS);
        assertThat(place.detail()).isNull();
    }
}
