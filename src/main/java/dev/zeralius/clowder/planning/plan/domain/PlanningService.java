package dev.zeralius.clowder.planning.plan.domain;

import dev.zeralius.clowder.planning.feasibility.RouteEvaluator;
import dev.zeralius.clowder.planning.plan.api.PlanRunRequest;
import dev.zeralius.clowder.planning.plan.api.PlanRunResponse;
import dev.zeralius.clowder.planning.plan.api.PlannedRouteResponse;
import dev.zeralius.clowder.planning.plan.api.PlannedStopResponse;
import dev.zeralius.clowder.planning.render.RoutePlanSvgRenderer;
import dev.zeralius.clowder.planning.solver.HeuristicSolver;
import dev.zeralius.clowder.planning.solver.Route;
import dev.zeralius.clowder.planning.solver.Solver;
import dev.zeralius.clowder.planning.solver.SolverConfig;
import dev.zeralius.clowder.planning.solver.SolverResult;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Load, solve, map. The synchronous shape of what M4 turns into a persisted, async plan run.
 *
 * <p><b>Deliberately not {@code @Transactional}.</b> The loader opens a short read-only transaction
 * and returns detached data; the solve that follows is CPU-bound for up to the request's budget, and
 * holding a pooled connection across it is how a connection pool is exhausted.
 */
@Service
public class PlanningService {

    private final PlanningProblemLoader loader;
    private final Solver solver;
    private final RouteEvaluator evaluator = new RouteEvaluator();
    private final RoutePlanSvgRenderer renderer = new RoutePlanSvgRenderer();

    /** Explicit: there are two constructors, so Spring cannot infer which one to use. */
    @Autowired
    public PlanningService(PlanningProblemLoader loader) {
        this(loader, new HeuristicSolver());
    }

    /** For tests that want to substitute the solver. */
    PlanningService(PlanningProblemLoader loader, Solver solver) {
        this.loader = loader;
        this.solver = solver;
    }

    public PlanRunResponse plan(PlanRunRequest request) {
        final var loaded = loader.load(request.depotId(), request.serviceDate());
        return toResponse(request, loaded, solve(loaded, request));
    }

    /** The same run, rendered. Nothing is stored, so this re-solves rather than reading a plan back. */
    public String planAsSvg(PlanRunRequest request) {
        final var loaded = loader.load(request.depotId(), request.serviceDate());
        final var result = solve(loaded, request);
        return renderer.render(result.solution(), loaded.instance(), loaded.points(), 0);
    }

    private SolverResult solve(PlanningProblemLoader.LoadedProblem loaded, PlanRunRequest request) {
        final var defaults = SolverConfig.defaults();
        final var config = new SolverConfig(
                request.seed() == null ? defaults.seed() : request.seed(),
                request.budgetSeconds() == null ? defaults.budget() : Duration.ofSeconds(request.budgetSeconds()),
                defaults.insertionAlpha(),
                defaults.insertionLambda(),
                defaults.maxNoImprovement());

        return solver.solve(loaded.instance(), config);
    }

    private PlanRunResponse toResponse(
            PlanRunRequest request, PlanningProblemLoader.LoadedProblem loaded, SolverResult result) {

        final var instance = loaded.instance();
        final var solution = result.solution();
        final var minorUnits = (double) instance.matrix().minorUnitsPerUnit();

        final var routes = new ArrayList<PlannedRouteResponse>();
        for (var route : solution.routes()) {
            if (!route.isEmpty()) {
                routes.add(toRoute(loaded, route, minorUnits));
            }
        }

        final var unassigned = new ArrayList<String>();
        for (var position : solution.unassigned()) {
            unassigned.add(loaded.consignments().get(position).reference());
        }

        return new PlanRunResponse(
                request.depotId(),
                request.serviceDate(),
                instance.stopCount(),
                solution.vehicleCount(),
                instance.vehicles().size(),
                solution.totalDistance(),
                solution.totalDistance() / minorUnits,
                List.copyOf(unassigned),
                result.elapsedMillis(),
                result.iterations(),
                result.config().seed(),
                result.solverVersion(),
                List.copyOf(routes));
    }

    /**
     * Per-stop times come from M2's evaluator rather than a third copy of the arithmetic. The solver
     * returns visit orders and totals; the schedule is the evaluator's job and always has been.
     */
    private PlannedRouteResponse toRoute(
            PlanningProblemLoader.LoadedProblem loaded, Route route, double minorUnits) {

        final var instance = loaded.instance();
        final var vehicle = instance.vehicles().stream()
                .filter(v -> v.id() == route.vehicleId())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Route references unknown vehicle " + route.vehicleId()));

        final var evaluation = evaluator.evaluate(vehicle, route.stopPositions(), instance);

        final var stops = new ArrayList<PlannedStopResponse>(evaluation.schedule().size());
        for (var visit = 0; visit < evaluation.schedule().size(); visit++) {
            final var scheduled = evaluation.schedule().get(visit);
            final var consignment = loaded.consignments().get(scheduled.stopIndex());
            stops.add(new PlannedStopResponse(
                    visit,
                    consignment.id(),
                    consignment.reference(),
                    consignment.addressLine(),
                    at(loaded.epoch(), scheduled.arrivalMinutes()),
                    at(loaded.epoch(), scheduled.serviceStartMinutes()),
                    at(loaded.epoch(), scheduled.departureMinutes()),
                    scheduled.waitMinutes(),
                    scheduled.loadAfter()));
        }

        return new PlannedRouteResponse(
                route.vehicleId(),
                route.stopCount(),
                route.distance(),
                route.distance() / minorUnits,
                route.durationMinutes(),
                List.copyOf(stops));
    }

    private static Instant at(Instant epoch, int minutes) {
        return epoch.plusSeconds(minutes * 60L);
    }
}
