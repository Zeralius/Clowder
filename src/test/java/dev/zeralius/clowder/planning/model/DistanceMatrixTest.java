package dev.zeralius.clowder.planning.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.zeralius.clowder.common.geo.DistanceProvider;
import dev.zeralius.clowder.common.geo.EuclideanDistanceProvider;
import dev.zeralius.clowder.common.geo.LatLon;
import java.util.List;
import org.junit.jupiter.api.Test;

class DistanceMatrixTest {

    /** A 60-unit square: (0,0), (60,0), (60,60), (0,60) with x as longitude. */
    private static final List<LatLon> SQUARE =
            List.of(LatLon.of(0d, 0d), LatLon.of(0d, 60d), LatLon.of(60d, 60d), LatLon.of(60d, 0d));

    private final DistanceMatrix matrix = DistanceMatrix.from(SQUARE, new EuclideanDistanceProvider());

    @Test
    void theDiagonalIsZero() {
        for (var i = 0; i < matrix.size(); i++) {
            assertThat(matrix.distance(i, i)).as("distance %d,%d", i, i).isZero();
            assertThat(matrix.travelTime(i, i)).as("time %d,%d", i, i).isZero();
        }
    }

    @Test
    void isSymmetricForAStraightLineProvider() {
        for (var i = 0; i < matrix.size(); i++) {
            for (var j = 0; j < matrix.size(); j++) {
                assertThat(matrix.distance(i, j)).isEqualTo(matrix.distance(j, i));
                assertThat(matrix.travelTime(i, j)).isEqualTo(matrix.travelTime(j, i));
            }
        }
    }

    /** Axis-aligned edges are exact: hypot(60, 0) is 60.0 with no rounding to argue about. */
    @Test
    void measuresTheSidesExactly() {
        assertThat(matrix.distance(0, 1)).isEqualTo(60_000_000L);
        assertThat(matrix.distance(1, 2)).isEqualTo(60_000_000L);
        assertThat(matrix.travelTime(0, 1)).isEqualTo(60);
    }

    /** The diagonal is 60 root 2 = 84.85281..., so allow a unit or two of floating-point slack. */
    @Test
    void measuresTheDiagonal() {
        assertThat(matrix.distance(0, 2)).isCloseTo(84_852_814L, within(2L));
        assertThat(matrix.travelTime(0, 2)).isEqualTo(85);
    }

    @Test
    void reportsTheProvidersMinorUnitScale() {
        assertThat(matrix.minorUnitsPerUnit()).isEqualTo(1_000_000L);
    }

    /** An asymmetric provider — a road network with one-way streets — gets every cell computed. */
    @Test
    void computesBothDirectionsForAnAsymmetricProvider() {
        var oneWay = new DistanceProvider() {
            @Override
            public long distanceBetween(LatLon from, LatLon to) {
                return from.longitude().compareTo(to.longitude()) < 0 ? 10L : 99L;
            }

            @Override
            public int travelTimeMinutes(LatLon from, LatLon to) {
                return from.longitude().compareTo(to.longitude()) < 0 ? 1 : 9;
            }

            @Override
            public long minorUnitsPerUnit() {
                return 1L;
            }

            @Override
            public boolean isSymmetric() {
                return false;
            }
        };

        var asymmetric = DistanceMatrix.from(SQUARE, oneWay);

        assertThat(asymmetric.distance(0, 1)).isEqualTo(10L);
        assertThat(asymmetric.distance(1, 0)).isEqualTo(99L);
    }
}
