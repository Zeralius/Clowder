package dev.zeralius.clowder.planning.feasibility;

import dev.zeralius.clowder.common.geo.EuclideanDistanceProvider;
import dev.zeralius.clowder.common.geo.LatLon;
import dev.zeralius.clowder.planning.model.DistanceMatrix;
import dev.zeralius.clowder.planning.model.ProblemInstance;
import dev.zeralius.clowder.planning.model.StopSpec;
import dev.zeralius.clowder.planning.model.VehicleSpec;
import java.util.List;

/**
 * Four collinear points so every pairwise distance is a whole number and expected schedules can be
 * worked out on paper. A square's diagonal is s root 2, never an integer, and a test whose expected
 * values you derived by running the code is not a test.
 *
 * <pre>
 *   index:      0        1              2                          3
 *   position:   0       10             30                         60
 *
 *   0-1 = 10    0-2 = 30    0-3 = 60    1-2 = 20    1-3 = 50    2-3 = 30
 * </pre>
 */
final class TestGrid {

    static final int DEPOT = 0;
    static final int SHIFT_MINUTES = 1000;
    static final long ROOMY_CAPACITY = 100_000L;

    private static final long MINOR_UNITS = 1_000_000L;

    static final DistanceMatrix LINE = DistanceMatrix.from(
            List.of(point(0), point(10), point(30), point(60)), new EuclideanDistanceProvider());

    private TestGrid() {}

    /** x maps to longitude, matching the importer and the Euclidean provider. */
    private static LatLon point(int x) {
        return LatLon.of(0d, x);
    }

    /** Distance in the matrix's minor units, so tests read in plane units. */
    static long units(long planeUnits) {
        return planeUnits * MINOR_UNITS;
    }

    static VehicleSpec vehicle() {
        return vehicle(SHIFT_MINUTES, ROOMY_CAPACITY, ROOMY_CAPACITY);
    }

    static VehicleSpec vehicle(int shiftMinutes) {
        return vehicle(shiftMinutes, ROOMY_CAPACITY, ROOMY_CAPACITY);
    }

    static VehicleSpec vehicle(int shiftMinutes, long capacityGrams, long capacityMillilitres) {
        return new VehicleSpec(1L, capacityGrams, capacityMillilitres, 0, shiftMinutes, DEPOT);
    }

    /** Consignment ids are {@code matrixIndex * 100} so failures name the stop unambiguously. */
    static StopSpec stop(int matrixIndex, int windowStart, int windowEnd, int serviceMinutes) {
        return stop(matrixIndex, windowStart, windowEnd, serviceMinutes, 0L, 0L);
    }

    static StopSpec stop(
            int matrixIndex,
            int windowStart,
            int windowEnd,
            int serviceMinutes,
            long weightGrams,
            long volumeMillilitres) {
        return new StopSpec(
                matrixIndex * 100L,
                matrixIndex,
                weightGrams,
                volumeMillilitres,
                serviceMinutes,
                windowStart,
                windowEnd);
    }

    static ProblemInstance instance(StopSpec... stops) {
        return new ProblemInstance(List.of(vehicle()), List.of(stops), LINE, DEPOT);
    }

    static int[] route(int... positions) {
        return positions;
    }
}
