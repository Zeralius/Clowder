package dev.zeralius.clowder.common.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class EuclideanDistanceProviderTest {

    private final DistanceProvider provider = new EuclideanDistanceProvider();

    @Test
    void measuresAThreeFourFiveTriangleExactly() {
        var origin = LatLon.of(0d, 0d);
        var corner = LatLon.of(4d, 3d); // latitude 4, longitude 3

        assertThat(provider.distanceBetween(origin, corner)).isEqualTo(5_000_000L);
        assertThat(provider.travelTimeMinutes(origin, corner)).isEqualTo(5);
    }

    /** Solomon instances have speed 1, so the two methods must agree once scaled. */
    @Test
    void travelTimeEqualsDistanceInPlaneUnits() {
        var a = LatLon.of(0d, 0d);
        var b = LatLon.of(30d, 40d);

        assertThat(provider.distanceBetween(a, b)).isEqualTo(50_000_000L);
        assertThat(provider.travelTimeMinutes(a, b)).isEqualTo(50);
    }

    @Test
    void keepsSubUnitPrecisionThatWholeUnitsWouldLose() {
        var a = LatLon.of(0d, 0d);
        var b = LatLon.of(1d, 1d); // root 2

        assertThat(provider.distanceBetween(a, b)).isCloseTo(1_414_214L, within(2L));
    }
}
