package com.solvelify.routeplanner.along;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.solvelify.routeplanner.planning.GeoPoint;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlatTest {

    private static final GeoPoint WEST = new GeoPoint(-36.90, 174.70);
    private static final GeoPoint EAST = new GeoPoint(-36.90, 174.80);

    @Test
    void measuresAHundredthOfADegreeNorthAsAKilometreAndAHundredAndTwelveMetres() {
        // 6,371 km times pi over 180, over a hundred.
        assertThat(Flat.metresBetween(WEST, new GeoPoint(-36.89, 174.70))).isCloseTo(1112, within(1.0));
    }

    @Test
    void measuresADegreeEastShorterTheFurtherFromTheEquator() {
        // At 36.9 degrees south a degree of longitude is cos(36.9) of a degree of latitude.
        assertThat(Flat.metresBetween(WEST, EAST)).isCloseTo(8892, within(5.0));
    }

    @Test
    void measuresAPointBesideAStretchToTheNearestSpotOnIt() {
        GeoPoint justNorthOfTheMiddle = new GeoPoint(-36.899, 174.75);

        assertThat(Flat.metresFromLine(justNorthOfTheMiddle, List.of(WEST, EAST))).isCloseTo(111, within(1.0));
    }

    @Test
    void measuresAPointPastTheEndOfAStretchToItsEnd() {
        GeoPoint pastTheEastEnd = new GeoPoint(-36.90, 174.81);

        assertThat(Flat.metresFromLine(pastTheEastEnd, List.of(WEST, EAST)))
                .isCloseTo(Flat.metresBetween(pastTheEastEnd, EAST), within(0.001));
    }

    @Test
    void measuresToTheNearestOfSeveralStretches() {
        GeoPoint north = new GeoPoint(-36.80, 174.80);
        GeoPoint besideTheSecond = new GeoPoint(-36.85, 174.801);

        assertThat(Flat.metresFromLine(besideTheSecond, List.of(WEST, EAST, north))).isCloseTo(89, within(1.0));
    }

    @Test
    void takesALineOfOnePointAsThatPoint() {
        assertThat(Flat.metresFromLine(EAST, List.of(WEST))).isEqualTo(Flat.metresBetween(EAST, WEST));
    }

    @Test
    void measuresNothingFromAPointOnTheLine() {
        assertThat(Flat.metresFromLine(new GeoPoint(-36.90, 174.73), List.of(WEST, EAST))).isCloseTo(0, within(0.001));
    }
}
