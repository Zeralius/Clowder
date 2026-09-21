package dev.zeralius.clowder.planning.solver;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.planning.feasibility.RouteEvaluator;
import org.junit.jupiter.api.Test;

class SolverFeasibilityTest {

    private final Solver solver = new HeuristicSolver();
    private final RouteEvaluator evaluator = new RouteEvaluator();

    /** The payoff for building M2 separately: an independent check on every route the solver returns. */
    @Test
    void everyReturnedRoutePassesTheEvaluator() {
        final var instance = TestInstances.twoClusters();
        final var result = solver.solve(instance, SolverConfig.defaults());

        for (var route : result.solution().routes()) {
            final var vehicle = instance.vehicles().stream()
                    .filter(v -> v.id() == route.vehicleId())
                    .findFirst()
                    .orElseThrow();

            assertThat(evaluator.evaluate(vehicle, route.stopPositions(), instance).violations())
                    .as("route %s", route)
                    .isEmpty();
        }
    }

    @Test
    void everyStopIsRoutedExactlyOnce() {
        final var instance = TestInstances.twoClusters();
        final var result = solver.solve(instance, SolverConfig.defaults());

        final var seen = new boolean[instance.stopCount()];
        for (var route : result.solution().routes()) {
            for (var position : route.stopPositions()) {
                assertThat(seen[position]).as("stop %d visited twice", position).isFalse();
                seen[position] = true;
            }
        }
        for (var position : result.solution().unassigned()) {
            seen[position] = true;
        }

        assertThat(seen).containsOnly(true);
    }

    /** Eight 30 g stops on 100 g vehicles: three is the floor, and four were on offer. */
    @Test
    void usesNoMoreVehiclesThanTheCapacityBoundRequires() {
        final var result = solver.solve(TestInstances.twoClusters(), SolverConfig.defaults());

        assertThat(result.solution().isComplete()).isTrue();
        assertThat(result.solution().vehicleCount()).isEqualTo(3);
    }

    @Test
    void localSearchNeverReturnsSomethingWorseThanConstruction() {
        final var instance = TestInstances.twoClusters();
        final var config = SolverConfig.defaults();

        final var constructed = new InsertionConstructor(instance, config).construct();
        final var solved = solver.solve(instance, config).solution();

        assertThat(Solution.LEXICOGRAPHIC.compare(solved, constructed)).isLessThanOrEqualTo(0);
    }

    @Test
    void reportsTheConfigAndVersionItRanWith() {
        final var result = solver.solve(TestInstances.line(), SolverConfig.defaults());

        assertThat(result.solverVersion()).isEqualTo(HeuristicSolver.VERSION);
        assertThat(result.config().seed()).isEqualTo(SolverConfig.defaults().seed());
    }
}
