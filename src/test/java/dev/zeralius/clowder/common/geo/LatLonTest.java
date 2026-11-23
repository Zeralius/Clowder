package dev.zeralius.clowder.common.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class LatLonTest {

    @Test
    void normalisesScaleSoDifferentlyWrittenCoordinatesAreEqual() {
        var terse = LatLon.of("51.65", "7.09");
        var padded = LatLon.of("51.650000", "7.090000");

        assertThat(terse).isEqualTo(padded);
        assertThat(terse).hasSameHashCodeAs(padded);
    }

    @Test
    void equalCoordinatesCollapseInAHashSet() {
        var set = new HashSet<>(List.of(LatLon.of("51.65", "7.09"), LatLon.of("51.650000", "7.090000")));

        assertThat(set).hasSize(1);
    }

    @Test
    void normalisesToScaleSix() {
        assertThat(LatLon.of("51.65", "7.09").latitude()).isEqualTo(new BigDecimal("51.650000"));
    }

    @Test
    void rejectsLatitudeOutOfRange() {
        assertThatThrownBy(() -> LatLon.of("90.5", "0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latitude");
    }

    @Test
    void rejectsLongitudeOutOfRange() {
        assertThatThrownBy(() -> LatLon.of("0", "180.5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longitude");
    }

    @Test
    void rejectsNulls() {
        assertThatThrownBy(() -> new LatLon(null, BigDecimal.ZERO)).isInstanceOf(NullPointerException.class);
    }
}
