package com.solvelify.routeplanner.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReferencePointTest {

    @Test
    void acceptsTheEdgesOfTheMap() {
        assertThat(new ReferencePoint(90, 180).latitude()).isEqualTo(90);
        assertThat(new ReferencePoint(-90, -180).longitude()).isEqualTo(-180);
    }

    @ParameterizedTest
    @ValueSource(doubles = {90.0001, -90.0001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void refusesALatitudeThatIsNotOnTheMap(double latitude) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ReferencePoint(latitude, 174.7866))
                .withMessageContaining("latitude");
    }

    @ParameterizedTest
    @ValueSource(doubles = {180.0001, -180.0001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void refusesALongitudeThatIsNotOnTheMap(double longitude) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ReferencePoint(-36.8712, longitude))
                .withMessageContaining("longitude");
    }
}
