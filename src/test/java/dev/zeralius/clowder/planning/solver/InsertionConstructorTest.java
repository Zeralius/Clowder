package dev.zeralius.clowder.planning.solver;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.common.geo.EuclideanDistanceProvider;
import dev.zeralius.clowder.common.geo.LatLon;
import dev.zeralius.clowder.planning.model.DistanceMatrix;
import dev.zeralius.clowder.planning.model.ProblemInstance;
import dev.zeralius.clowder.planning.model.StopSpec;
import dev.zeralius.clowder.planning.model.VehicleSpec;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * I1 against a fixture small enough to run by hand. Same collinear line as M2's tests — depot at 0,
 * stops at 10, 30 and 60 — so every c11 and c12 is a whole number you can check on paper. Each test's
 * javadoc is the working.
 *
 * <p>Stops are declared in matrix order, so position {@code p} in the instance is matrix index
 * {@code p + 1}: position 0 is the stop at 10, position 1 at 30, position 2 at 60.
 */
class InsertionConstructorTest {

    private static final long UNIT = 1_000_000L;
    private static final int DEPOT = 0;

    private static final DistanceMatrix LINE = DistanceMatrix.from(
            List.of(LatLon.of(0d, 0d), LatLon.of(0d, 10d), LatLon.of(0d, 30d), LatLon.of(0d, 60d)),
            new EuclideanDistanceProvider());

    private static SolverConfig config(double alpha) {
        return new SolverConfig(1L, Duration.ofSeconds(1), alpha, 2.0d, 50);
    }

    private static VehicleSpec vehicle(long id, long capacityGrams) {
        return new VehicleSpec(id, capacityGrams, Long.MAX_VALUE, 0, 1000, DEPOT);
    }

    private static StopSpec stop(int matrixIndex, int windowStart, int windowEnd, long weightGrams) {
        return new StopSpec(matrixIndex * 100L, matrixIndex, weightGrams, 0L, 0, windowStart, windowEnd);
    }

    private static StopSpec openStop(int matrixIndex) {
        return stop(matrixIndex, 0, 1000, 0L);
    }

    /**
     * Seed is the furthest customer, 60. Then for 10 and 30 every position has {@code c11 = 0} — on a
     * line, a detour through a point between two others adds nothing — so c2 is pure {@code λ·d(depot,u)}
     * and 30 (c2 = 60) beats 10 (c2 = 20). Route [30, 60]. Then 10 goes to the first position on a
     * tie between positions 0 and 2. One vehicle, [10, 30, 60], 10 + 20 + 30 + 60 = 120.
     */
    @Test
    void buildsOneRouteAlongTheLineInOrder() {
        var instance = new ProblemInstance(
                List.of(vehicle(1L, 1_000L), vehicle(2L, 1_000L)),
                List.of(openStop(1), openStop(2), openStop(3)),
                LINE,
                DEPOT);

        var solution = new InsertionConstructor(instance, config(1.0d)).construct();

        assertThat(solution.isComplete()).isTrue();
        assertThat(solution.vehicleCount()).isEqualTo(1);
        assertThat(solution.routes()).hasSize(1);
        assertThat(solution.routes().get(0).stopPositions()).containsExactly(0, 1, 2);
        assertThat(solution.totalDistance()).isEqualTo(120 * UNIT);
    }

    /**
     * Three 600 g stops against a 1000 g limit. Vehicle 1 seeds with 60; nothing else fits beside it.
     * Vehicle 2 seeds with the furthest remaining, 30; 10 does not fit beside that either. Out of
     * vehicles, so 10 stays unassigned — reported, not silently dropped.
     */
    @Test
    void opensANewRouteWhenCapacityIsFullAndReportsWhatIsLeftOver() {
        var instance = new ProblemInstance(
                List.of(vehicle(1L, 1_000L), vehicle(2L, 1_000L)),
                List.of(stop(1, 0, 1000, 600L), stop(2, 0, 1000, 600L), stop(3, 0, 1000, 600L)),
                LINE,
                DEPOT);

        var solution = new InsertionConstructor(instance, config(1.0d)).construct();

        assertThat(solution.isComplete()).isFalse();
        assertThat(solution.unassigned()).containsExactly(0);
        assertThat(solution.routes()).extracting(Route::stopPositions).containsExactly(new int[] {2}, new int[] {1});
        assertThat(solution.routes()).extracting(Route::vehicleId).containsExactly(1L, 2L);
        assertThat(solution.totalDistance()).isEqualTo((120 + 60) * UNIT);
    }

    /**
     * Stop 10 opens at minute 100; stop 60 is open all day. Seed 60 (served at 60). Inserting 10 first
     * means waiting until 100 and reaching 60 at 150: c11 = 0 but c12 = 90. Inserting 10 last means
     * arriving at 110, inside its window: c11 = 0, c12 = 0. With α1 = 1 the delay is invisible and the
     * tie goes to position 0; with α1 = 0.5 the delay costs 45 and position 1 wins.
     */
    @Test
    void weighsDelayedServiceOnlyWhenAlphaIsBelowOne() {
        var instance = new ProblemInstance(
                List.of(vehicle(1L, 1_000L)), List.of(stop(1, 100, 1000, 0L), openStop(3)), LINE, DEPOT);

        var distanceOnly = new InsertionConstructor(instance, config(1.0d)).construct();
        var withDelay = new InsertionConstructor(instance, config(0.5d)).construct();

        assertThat(distanceOnly.routes().get(0).stopPositions()).containsExactly(0, 1);
        assertThat(withDelay.routes().get(0).stopPositions()).containsExactly(1, 0);
    }

    /** A customer no single-stop route can reach is left unassigned rather than breaking the run. */
    @Test
    void leavesAnUnreachableCustomerUnassigned() {
        var instance = new ProblemInstance(
                List.of(vehicle(1L, 1_000L)), List.of(openStop(1), stop(3, 0, 50, 0L)), LINE, DEPOT);

        var solution = new InsertionConstructor(instance, config(1.0d)).construct();

        assertThat(solution.routes().get(0).stopPositions()).containsExactly(0);
        assertThat(solution.unassigned()).containsExactly(1);
    }

    @Test
    void isDeterministic() {
        var instance = new ProblemInstance(
                List.of(vehicle(1L, 1_000L), vehicle(2L, 1_000L)),
                List.of(openStop(1), openStop(2), openStop(3)),
                LINE,
                DEPOT);

        var first = new InsertionConstructor(instance, config(1.0d)).construct();
        var second = new InsertionConstructor(instance, config(1.0d)).construct();

        assertThat(first).isEqualTo(second);
    }
}
