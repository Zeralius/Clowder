package dev.zeralius.clowder.planning.solver;

import dev.zeralius.clowder.planning.feasibility.RouteEvaluator;
import dev.zeralius.clowder.planning.model.ProblemInstance;
import dev.zeralius.clowder.planning.model.VehicleSpec;
import java.util.ArrayList;
import java.util.List;

/**
 * Solomon I1 construction followed by local search.
 *
 * <p>Takes a plain problem struct, returns a plain solution. No database, no Spring, and never inside
 * a transaction: holding a pooled connection across a multi-second CPU-bound operation is how a
 * connection pool is exhausted.
 *
 * <p>Every route in the answer is re-run through M2's {@code RouteEvaluator} before it is returned,
 * and stops are counted. It costs microseconds and makes two whole classes of bug — a solver that
 * quietly returns infeasible routes, and an off-by-one in {@code System.arraycopy} that serves a
 * customer twice without making anything infeasible — impossible to ship.
 */
public final class HeuristicSolver implements Solver {

    /** Bump on any change that can alter output. Stored on {@code plan_run} in M4. */
    public static final String VERSION = "i1+elim+relocate+2opt+oropt+ils/3";

    private final RouteEvaluator evaluator = new RouteEvaluator();

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public SolverResult solve(ProblemInstance instance, SolverConfig config) {
        final var startedAt = System.nanoTime();
        final var deadline = Deadline.startingNow(config.budget());

        final var constructed = new InsertionConstructor(instance, config).construct();
        final var optimised = new LocalSearchOptimiser(instance, config).optimise(constructed, deadline);

        assertFeasible(optimised.solution(), instance);

        final var elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        return new SolverResult(optimised.solution(), config, elapsedMillis, optimised.iterations(), VERSION);
    }

    /** Re-checks the answer with M2's evaluator. A violation here is a solver bug, not a bad input. */
    private void assertFeasible(Solution solution, ProblemInstance instance) {
        final var byId = new ArrayList<VehicleSpec>(instance.vehicles());

        for (var route : solution.routes()) {
            final var vehicle = byId.stream()
                    .filter(v -> v.id() == route.vehicleId())
                    .findFirst()
                    .orElseThrow(
                            () -> new IllegalStateException("Route references unknown vehicle " + route.vehicleId()));

            final var evaluation = evaluator.evaluate(vehicle, route.stopPositions(), instance);
            if (!evaluation.feasible()) {
                throw new IllegalStateException("Solver returned an infeasible route for vehicle %d: %s"
                        .formatted(route.vehicleId(), evaluation.violations()));
            }
        }

        final var routed = solution.routes().stream().mapToInt(Route::stopCount).sum();
        if (routed + solution.unassigned().length != instance.stopCount()) {
            throw new IllegalStateException("Solver lost or duplicated stops: %d routed + %d unassigned != %d total"
                    .formatted(routed, solution.unassigned().length, instance.stopCount()));
        }

        final var seen = new boolean[instance.stopCount()];
        for (var route : solution.routes()) {
            for (var position : route.stopPositions()) {
                if (seen[position]) {
                    throw new IllegalStateException("Stop " + position + " appears on more than one route");
                }
                seen[position] = true;
            }
        }
    }

    /** Convenience for callers that only want the routes. */
    public static List<Route> routesOf(SolverResult result) {
        return result.solution().routes();
    }
}
