package dev.zeralius.clowder.planning.solver;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class SolverDeterminismTest {

    private final Solver solver = new HeuristicSolver();

    @Test
    void sameInstanceAndSeedProducesIdenticalRoutes() {
        final var instance = TestInstances.twoClusters();
        final var config = SolverConfig.defaults();

        final var first = solver.solve(instance, config);
        final var second = solver.solve(instance, config);

        assertThat(second.solution().totalDistance()).isEqualTo(first.solution().totalDistance());
        assertThat(second.solution().vehicleCount()).isEqualTo(first.solution().vehicleCount());
        // Route equality is value-based, so this compares composition and order, not identity.
        assertThat(second.solution().routes()).isEqualTo(first.solution().routes());
    }

    /** Repeated because a nondeterminism bug that fires one run in ten passes a single-shot test. */
    @RepeatedTest(10)
    void repeatedRunsDoNotDrift() {
        final var instance = TestInstances.line();
        final var config = SolverConfig.defaults();

        assertThat(solver.solve(instance, config).solution())
                .isEqualTo(solver.solve(instance, config).solution());
    }
}
