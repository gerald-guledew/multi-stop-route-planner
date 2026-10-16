package com.solvelify.routeplanner.cars;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.solvelify.routeplanner.cars.CarWeights.Rated;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The register's weights, as the 2018 Swift 1.2 and others carry them. */
class CarWeightsTest {

    private static final Rated SWIFT_SOLD_NEW = new Rated(false, 1365, 5, 1344);
    private static final Rated SWIFT_IMPORTED = new Rated(true, 1165, 5, 552);

    @Test
    void takesJapansWeightLessFiftyFiveKilogramsASeat() {
        assertThat(CarWeights.weightOf(List.of(SWIFT_SOLD_NEW, SWIFT_IMPORTED))).hasValue(890);
        assertThat(CarWeights.weightOf(List.of(SWIFT_IMPORTED))).hasValue(890);
    }

    @Test
    void takesTheMakersMostLoadedAsTheCarAndThirtySixPercentMore() {
        // A 2019 RAV4 2.5, sold new only.
        assertThat(CarWeights.weightOf(List.of(new Rated(false, 2215, 5, 1175))).getAsDouble())
                .isCloseTo(1629, within(1.0));
    }

    @Test
    void takesAnImportRatedLikeTheCarSoldNewForOneFromElsewhereThanJapan() {
        // A 2015 Hyundai i30 1.8 is 1,840 kg sold new here and imported used.
        List<Rated> i30 = List.of(new Rated(false, 1840, 5, 627), new Rated(true, 1840, 5, 29));

        assertThat(CarWeights.weightOf(i30).getAsDouble()).isCloseTo(1353, within(1.0));
    }

    @Test
    void readsFiveSeatsWhenTheRegisterGivesNone() {
        assertThat(CarWeights.weightOf(List.of(new Rated(true, 1165, null, 10)))).hasValue(890);
    }

    @Test
    void hasNoWeightForAnEngineTheRegisterDoesNotWeigh() {
        assertThat(CarWeights.weightOf(List.of(new Rated(false, 0, 5, 2041), new Rated(true, null, 5, 30)))).isEmpty();
    }

    @Test
    void takesTheMiddleWeightOfManyEnginesCountedByTheirCars() {
        List<List<Rated>> engines = List.of(
                List.of(new Rated(true, 1165, 5, 100)),
                List.of(new Rated(true, 1275, 5, 300)),
                List.of(new Rated(true, 1575, 5, 50)),
                List.of(new Rated(false, 0, 5, 1000)));

        // 890, 1,000 and 1,300 kg. The one without a weight does not count.
        assertThat(CarWeights.middleOf(engines)).hasValue(1000);
    }

    @Test
    void hasNoMiddleWhenNoEngineIsWeighed() {
        assertThat(CarWeights.middleOf(List.of(List.of(new Rated(false, 0, 5, 10))))).isEmpty();
    }
}
