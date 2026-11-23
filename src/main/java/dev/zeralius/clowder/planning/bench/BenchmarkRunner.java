package dev.zeralius.clowder.planning.bench;

import dev.zeralius.clowder.planning.model.ProblemInstance;
import dev.zeralius.clowder.planning.solver.Solver;
import dev.zeralius.clowder.planning.solver.SolverConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Solves each instance once and scores it against the published best-known value.
 *
 * <p>Deliberately not a Spring bean and not a test that runs on every build: benchmarking on every CI
 * run makes builds slow and flaky. Run it deliberately, commit the CSV, note the machine.
 */
public final class BenchmarkRunner {

    private final Solver solver;
    private final BestKnownRegistry bestKnown;

    public BenchmarkRunner(Solver solver, BestKnownRegistry bestKnown) {
        this.solver = solver;
        this.bestKnown = bestKnown;
    }

    /**
     * @param names instance names, in the order they should appear in the table
     * @param instances resolves a name to its problem
     */
    public List<BenchmarkResult> run(
            List<String> names, Function<String, ProblemInstance> instances, SolverConfig config) {
        final var results = new ArrayList<BenchmarkResult>(names.size());

        for (var name : names) {
            final var instance = instances.apply(name);
            final var result = solver.solve(instance, config);

            final var minorUnits = (double) instance.matrix().minorUnitsPerUnit();
            final var distance = result.solution().totalDistance() / minorUnits;

            final var reference = bestKnown.find(name).orElse(new BestKnownRegistry.BestKnown(name, 0, Double.NaN));

            results.add(BenchmarkResult.of(
                    name,
                    result.solution().vehicleCount(),
                    distance,
                    result.solution().unassigned().length,
                    reference.vehicles(),
                    reference.distance(),
                    result.elapsedMillis()));
        }

        return results;
    }
}
