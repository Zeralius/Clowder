package dev.zeralius.clowder.common.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * One degree of latitude is the great-circle distance you can verify with arithmetic rather than a
 * lookup: a meridian degree is 2 pi R / 360, and with R = 6 371 008.8 m that is 111 194.9 m.
 */
class HaversineDistanceProviderTest {

    private final DistanceProvider provider = new HaversineDistanceProvider(60d);

    @Test
    void identicalPointsAreZeroApart() {
        var marl = LatLon.of("51.656", "7.090");

        assertThat(provider.distanceBetween(marl, marl)).isZero();
        assertThat(provider.travelTimeMinutes(marl, marl)).isZero();
    }

    /** 2 pi R / 360 with R = 6371008.8 m is 111194.9 m. */
    @Test
    void measuresOneDegreeOfLatitude() {
        var south = LatLon.of(0d, 0d);
        var north = LatLon.of(1d, 0d);

        assertThat(provider.distanceBetween(south, north)).isCloseTo(111_195L, within(5L));
    }

    /** 60 km/h is exactly 1000 m per minute, so 111195 m is 111 minutes. */
    @Test
    void derivesTravelTimeFromTheAverageSpeed() {
        assertThat(provider.travelTimeMinutes(LatLon.of(0d, 0d), LatLon.of(1d, 0d)))
                .isEqualTo(111);
    }

    @Test
    void isSymmetric() {
        var a = LatLon.of("51.656", "7.090");
        var b = LatLon.of("51.5136", "7.4653");

        assertThat(provider.distanceBetween(a, b)).isEqualTo(provider.distanceBetween(b, a));
        assertThat(provider.isSymmetric()).isTrue();
    }

    @Test
    void reportsWholeMetresAsItsMinorUnit() {
        assertThat(provider.minorUnitsPerUnit()).isEqualTo(1L);
    }

    @Test
    void rejectsANonPositiveSpeed() {
        assertThatThrownBy(() -> new HaversineDistanceProvider(0d)).isInstanceOf(IllegalArgumentException.class);
    }
}
