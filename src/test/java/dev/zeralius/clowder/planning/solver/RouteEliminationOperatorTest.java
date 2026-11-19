package dev.zeralius.clowder.planning.solver;

import static dev.zeralius.clowder.planning.solver.TestInstances.solution;
import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.common.geo.DistanceProvider;
import dev.zeralius.clowder.common.geo.EuclideanDistanceProvider;
import dev.zeralius.clowder.common.geo.LatLon;
import dev.zeralius.clowder.planning.model.DistanceMatrix;
import dev.zeralius.clowder.planning.model.ProblemInstance;
import dev.zeralius.clowder.planning.model.StopSpec;
import dev.zeralius.clowder.planning.model.VehicleSpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Two fixtures, because the interesting behaviour only shows on one of them.
 *
 * <p>On a metric matrix, merging two routes can never cost distance: you drop one depot leg and the
 * triangle inequality covers the join. So on ordinary geometry relocate can usually unpick a short
 * route by itself, and elimination is only tidier. What it is actually for is the case where a
 * single move looks bad in isolation and the whole package is still worth taking, which needs a
 * matrix that does not obey the triangle inequality. A road network is one; so is the one below.
 */
class RouteEliminationOperatorTest {

    // ---- fixtures ----

    /** Ordinary geometry: a near cluster and a far one, both on a line out from the depot. */
    private static ProblemInstance metric(int vehicles, long capacityGrams) {
        var points = new ArrayList<LatLon>();
        points.add(LatLon.of(0d, 0d));
        for (var i = 0; i < 3; i++) {
            points.add(LatLon.of(0d, 10d + i));
        }
        for (var i = 0; i < 3; i++) {
            points.add(LatLon.of(0d, 80d + i));
        }
        return instance(
                points, 6, DistanceMatrix.from(points, new EuclideanDistanceProvider()), vehicles, capacityGrams);
    }

    /**
     * Cheap to reach any stop from the depot, expensive to travel between stops. Merging routes
     * therefore costs a great deal, which is what makes every single-stop move look like a loss.
     */
    private static ProblemInstance nonMetric(int vehicles) {
        var points = new ArrayList<LatLon>();
        points.add(LatLon.of(0d, 0d));
        for (var i = 1; i <= 4; i++) {
            points.add(LatLon.of(0d, i));
        }
        var provider = new DistanceProvider() {
            @Override
            public long distanceBetween(LatLon from, LatLon to) {
                if (from.equals(to)) {
                    return 0L;
                }
                var atDepot = from.longitude().signum() == 0 || to.longitude().signum() == 0;
                return atDepot ? 1L : 100L;
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
        return instance(points, 4, DistanceMatrix.from(points, provider), vehicles, 1_000L);
    }

    private static ProblemInstance instance(
            List<LatLon> points, int stopCount, DistanceMatrix matrix, int vehicles, long capacityGrams) {
        var stops = new ArrayList<StopSpec>();
        for (var i = 1; i <= stopCount; i++) {
            stops.add(new StopSpec(i * 100L, i, 10L, 0L, 0, 0, 10_000));
        }
        var specs = new ArrayList<VehicleSpec>();
        for (var v = 1; v <= vehicles; v++) {
            specs.add(new VehicleSpec(v, capacityGrams, Long.MAX_VALUE, 0, 10_000, 0));
        }
        return new ProblemInstance(specs, stops, matrix, 0);
    }

    // ---- what the operator is for ----

    /**
     * The case relocate cannot reach. Pulling either stop out of the two-stop route leaves the other
     * one still paying both depot legs, so the source barely shrinks while the target gains a 100
     * unit hop. The move is a wash, nothing empties, and relocate declines. Taking both at once
     * retires the vehicle.
     */
    @Test
    void succeedsWhereSingleStopRelocateIsStuck() {
        var instance = nonMetric(2);
        // Both routes hold two stops, so no single move can empty either one.
        var start = solution(instance, new int[] {0, 1}, new int[] {2, 3});

        var forRelocate = SolutionState.of(start, instance);
        assertThat(new RelocateOperator().improve(forRelocate))
                .as("relocate judges each move on distance alone and finds nothing")
                .isFalse();

        var forElimination = SolutionState.of(start, instance);
        assertThat(new RouteEliminationOperator().improve(forElimination)).isTrue();
        assertThat(forElimination.toSolution().vehicleCount()).isEqualTo(1);
        assertThat(allStops(forElimination)).containsExactlyInAnyOrder(0, 1, 2, 3);
    }

    /** Vehicle count is the first term of the objective, so a longer solution is the right trade. */
    @Test
    void acceptsALongerSolutionToRetireAVehicle() {
        var instance = nonMetric(2);
        var state = SolutionState.of(solution(instance, new int[] {0, 1}, new int[] {2, 3}), instance);
        var before = state.totalDistance();

        assertThat(new RouteEliminationOperator().improve(state)).isTrue();

        assertThat(state.totalDistance()).isGreaterThan(before);
        assertThat(state.toSolution().vehicleCount()).isEqualTo(1);
    }

    /** End to end: the full operator set retires a vehicle the old set left on the road. */
    @Test
    void theOptimiserRetiresAVehicleTheOldOperatorSetCouldNot() {
        var instance = nonMetric(2);
        var start = solution(instance, new int[] {0, 1}, new int[] {2, 3});

        var without = new LocalSearchOptimiser(
                        instance,
                        SolverConfig.defaults(),
                        List.of(new RelocateOperator(), new TwoOptOperator(), new OrOptOperator()))
                .optimise(start, Deadline.startingNow(Duration.ofSeconds(5)));
        var with = new LocalSearchOptimiser(instance, SolverConfig.defaults())
                .optimise(start, Deadline.startingNow(Duration.ofSeconds(5)));

        assertThat(without.solution().vehicleCount()).isEqualTo(2);
        assertThat(with.solution().vehicleCount()).isEqualTo(1);
        assertThat(with.solution().isComplete()).isTrue();
    }

    // ---- structure ----

    @Test
    void emptiesARouteByDistributingAllOfItsStops() {
        var instance = metric(3, 1_000L);
        var state = SolutionState.of(
                solution(instance, new int[] {0, 1, 2}, new int[] {3, 4}, new int[] {5}), instance);

        assertThat(new RouteEliminationOperator().improve(state)).isTrue();

        assertThat(state.toSolution().vehicleCount()).isEqualTo(2);
        assertThat(state.toSolution().isComplete()).isTrue();
        assertThat(allStops(state)).containsExactlyInAnyOrder(0, 1, 2, 3, 4, 5);
    }

    /** All or nothing. Three stops of 10 g into routes already holding 30 g against a 35 g limit. */
    @Test
    void changesNothingWhenOneStopCannotBePlaced() {
        var instance = metric(2, 35L);
        var state = SolutionState.of(
                solution(instance, new int[] {0, 1, 2}, new int[] {3, 4, 5}), instance);
        var before = snapshot(state);

        assertThat(new RouteEliminationOperator().improve(state)).isFalse();

        assertThat(snapshot(state)).isEqualTo(before);
        assertThat(state.toSolution().vehicleCount()).isEqualTo(2);
    }

    /** Retiring one vehicle only to start a spare would be no gain at all. */
    @Test
    void neverMovesStopsIntoAnEmptyRoute() {
        var instance = metric(4, 1_000L);
        var state = SolutionState.of(
                solution(instance, new int[] {0, 1, 2}, new int[] {3, 4, 5}, new int[0], new int[0]),
                instance);

        new RouteEliminationOperator().improve(state);

        assertThat(state.route(2)).isEmpty();
        assertThat(state.route(3)).isEmpty();
        assertThat(state.toSolution().vehicleCount()).isEqualTo(1);
    }

    @Test
    void doesNothingWhenThereIsOnlyOneRoute() {
        var instance = metric(2, 1_000L);
        var state = SolutionState.of(solution(instance, new int[] {0, 1, 2}, new int[0]), instance);

        assertThat(new RouteEliminationOperator().improve(state)).isFalse();
        assertThat(state.route(0)).containsExactly(0, 1, 2);
    }

    @Test
    void isDeterministic() {
        var instance = metric(3, 1_000L);
        var start = solution(instance, new int[] {0, 1, 2}, new int[] {3, 4}, new int[] {5});

        var first = SolutionState.of(start, instance);
        var second = SolutionState.of(start, instance);
        new RouteEliminationOperator().improve(first);
        new RouteEliminationOperator().improve(second);

        assertThat(snapshot(second)).isEqualTo(snapshot(first));
    }

    // ---- helpers ----

    private static List<Integer> allStops(SolutionState state) {
        var all = new ArrayList<Integer>();
        for (var v = 0; v < state.routeCount(); v++) {
            for (var position : state.route(v)) {
                all.add(position);
            }
        }
        return all;
    }

    private static List<String> snapshot(SolutionState state) {
        var out = new ArrayList<String>();
        for (var v = 0; v < state.routeCount(); v++) {
            out.add(Arrays.toString(state.route(v)));
        }
        return out;
    }
}
