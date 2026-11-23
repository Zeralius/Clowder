package dev.zeralius.clowder.planning.solver;

import static dev.zeralius.clowder.planning.solver.TestInstances.UNIT;
import static dev.zeralius.clowder.planning.solver.TestInstances.solution;
import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.common.geo.DistanceProvider;
import dev.zeralius.clowder.common.geo.LatLon;
import dev.zeralius.clowder.planning.model.DistanceMatrix;
import dev.zeralius.clowder.planning.model.ProblemInstance;
import dev.zeralius.clowder.planning.model.StopSpec;
import dev.zeralius.clowder.planning.model.VehicleSpec;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Each operator on the collinear line, where the optimum is easy to state: any route that goes out
 * once and comes back once costs twice the furthest stop, 120, and anything that doubles back costs
 * more.
 */
class LocalSearchOperatorTest {

    private final ProblemInstance line = TestInstances.line();

    /** [30, 10, 60] doubles back: 30 + 20 + 50 + 60 = 160. Moving the chain [10] to the front gives 120. */
    @Test
    void orOptMovesAChainToRemoveADoubleBack() {
        var state = SolutionState.of(solution(line, new int[] {1, 0, 2}, new int[0]), line);
        assertThat(state.distance(0)).isEqualTo(160 * UNIT);

        assertThat(new OrOptOperator().improve(state)).isTrue();

        assertThat(state.distance(0)).isEqualTo(120 * UNIT);
        assertThat(state.route(0)).containsExactly(0, 1, 2);
    }

    /**
     * [60, 10, 30] costs 60 + 50 + 20 + 30 = 160. Reversing the first two gives [10, 60, 30]: 10 + 50 +
     * 30 + 30 = 120, and the delta d(depot,10) + d(60,30) - d(depot,60) - d(10,30) = -40 is the first
     * negative one the scan meets.
     */
    @Test
    void twoOptReversesASegmentToRemoveACrossing() {
        var state = SolutionState.of(solution(line, new int[] {2, 0, 1}, new int[0]), line);
        assertThat(state.distance(0)).isEqualTo(160 * UNIT);

        assertThat(new TwoOptOperator().improve(state)).isTrue();

        assertThat(state.distance(0)).isEqualTo(120 * UNIT);
        assertThat(state.route(0)).containsExactly(0, 2, 1);
    }

    /** Two single-stop routes, 20 + 120 = 140. Merging them is 10 + 50 + 60 = 120 and frees a vehicle. */
    @Test
    void relocateEmptiesARouteAndFreesAVehicle() {
        var state = SolutionState.of(solution(line, new int[] {0}, new int[] {2}), line);

        assertThat(new RelocateOperator().improve(state)).isTrue();

        assertThat(state.route(0)).isEmpty();
        assertThat(state.route(1)).containsExactly(0, 2);
        assertThat(state.totalDistance()).isEqualTo(120 * UNIT);
        assertThat(state.toSolution().vehicleCount()).isEqualTo(1);
    }

    /** Once every stop is on one route, there is nothing for relocate to do: the other route is empty. */
    @Test
    void relocateNeverMovesAStopIntoAnEmptyRoute() {
        var state = SolutionState.of(solution(line, new int[] {0, 1, 2}, new int[0]), line);

        assertThat(new RelocateOperator().improve(state)).isFalse();
        assertThat(state.route(1)).isEmpty();
    }

    /**
     * On a metric, emptying a route never lengthens the total (triangle inequality), so the "accept a
     * longer solution to lose a vehicle" branch only ever fires on a road network. This fakes one: a
     * matrix where the merged route is much longer than the two singles. Relocate must still merge,
     * and must then stop — not move the stop back out into the now-empty route because that would
     * shorten the distance, and back in because that would free a vehicle, until the deadline.
     */
    @Test
    void relocateDoesNotPingPongOnANonMetricMatrix() {
        var nonMetric = new DistanceProvider() {
            /** depot to either stop is 1; between the stops is 100. Nothing like a plane. */
            @Override
            public long distanceBetween(LatLon from, LatLon to) {
                if (from.equals(to)) {
                    return 0L;
                }
                var fromDepot = from.longitude().signum() == 0;
                var toDepot = to.longitude().signum() == 0;
                return fromDepot || toDepot ? 1L : 100L;
            }

            @Override
            public int travelTimeMinutes(LatLon from, LatLon to) {
                return (int) distanceBetween(from, to);
            }

            @Override
            public long minorUnitsPerUnit() {
                return 1L;
            }
        };
        var matrix = DistanceMatrix.from(List.of(LatLon.of(0d, 0d), LatLon.of(0d, 1d), LatLon.of(0d, 2d)), nonMetric);
        var instance = new ProblemInstance(
                List.of(new VehicleSpec(1L, 100L, 100L, 0, 1000, 0), new VehicleSpec(2L, 100L, 100L, 0, 1000, 0)),
                List.of(new StopSpec(1L, 1, 0L, 0L, 0, 0, 1000), new StopSpec(2L, 2, 0L, 0L, 0, 0, 1000)),
                matrix,
                0);
        var state = SolutionState.of(solution(instance, new int[] {0}, new int[] {1}), instance);
        assertThat(state.totalDistance()).isEqualTo(4L);

        var relocate = new RelocateOperator();
        assertThat(relocate.improve(state)).isTrue();
        assertThat(state.toSolution().vehicleCount()).isEqualTo(1);
        assertThat(state.totalDistance()).isEqualTo(102L);

        assertThat(relocate.improve(state))
                .as("the reverse move must not be taken")
                .isFalse();

        var outcome = new LocalSearchOptimiser(instance, SolverConfig.defaults())
                .optimise(
                        solution(instance, new int[] {0}, new int[] {1}), Deadline.startingNow(Duration.ofSeconds(5)));
        assertThat(outcome.iterations()).isLessThan(5);
        assertThat(outcome.solution().vehicleCount()).isEqualTo(1);
    }

    @Test
    void theOptimiserReachesTheLineOptimumFromABadStart() {
        var start = solution(line, new int[] {2, 0}, new int[] {1});
        var outcome = new LocalSearchOptimiser(line, SolverConfig.defaults())
                .optimise(start, Deadline.startingNow(Duration.ofSeconds(5)));

        assertThat(outcome.solution().vehicleCount()).isEqualTo(1);
        assertThat(outcome.solution().totalDistance()).isEqualTo(120 * UNIT);
        assertThat(outcome.solution().isComplete()).isTrue();
    }
}
