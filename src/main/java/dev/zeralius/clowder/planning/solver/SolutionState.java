package dev.zeralius.clowder.planning.solver;

import dev.zeralius.clowder.planning.model.ProblemInstance;
import dev.zeralius.clowder.planning.model.VehicleSpec;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable solution under optimisation. One entry per vehicle, in the instance's vehicle order — which
 * is what keeps iteration deterministic. Operators read and replace whole routes; distances are
 * cached so the optimiser never recomputes a route it did not touch.
 */
final class SolutionState {

    private final ProblemInstance instance;
    private final List<VehicleSpec> vehicles;
    private final List<int[]> routes;
    private final long[] distances;
    private final int[] unassigned;

    private SolutionState(ProblemInstance instance, List<VehicleSpec> vehicles, List<int[]> routes, int[] unassigned) {
        this.instance = instance;
        this.vehicles = vehicles;
        this.routes = routes;
        this.unassigned = unassigned;
        this.distances = new long[routes.size()];
        for (var v = 0; v < routes.size(); v++) {
            distances[v] = RouteCalculator.distance(routes.get(v), routes.get(v).length, vehicles.get(v), instance);
        }
    }

    static SolutionState of(Solution solution, ProblemInstance instance) {
        final var vehicles = new ArrayList<VehicleSpec>();
        final var routes = new ArrayList<int[]>();

        // Index solution routes by vehicle id so the state keeps the instance's vehicle ordering.
        for (var vehicle : instance.vehicles()) {
            final var match = solution.routes().stream()
                    .filter(r -> r.vehicleId() == vehicle.id())
                    .findFirst();
            vehicles.add(vehicle);
            routes.add(match.map(Route::stopPositions).orElseGet(() -> new int[0]));
        }

        return new SolutionState(instance, vehicles, routes, solution.unassigned());
    }

    ProblemInstance instance() {
        return instance;
    }

    int routeCount() {
        return routes.size();
    }

    int[] route(int v) {
        return routes.get(v);
    }

    VehicleSpec vehicle(int v) {
        return vehicles.get(v);
    }

    long distance(int v) {
        return distances[v];
    }

    boolean isFeasible(int v, int[] candidate) {
        return RouteCalculator.isFeasible(candidate, candidate.length, vehicles.get(v), instance);
    }

    long distanceOf(int v, int[] candidate) {
        return RouteCalculator.distance(candidate, candidate.length, vehicles.get(v), instance);
    }

    void replace(int v, int[] newRoute) {
        routes.set(v, newRoute);
        distances[v] = RouteCalculator.distance(newRoute, newRoute.length, vehicles.get(v), instance);
    }

    long totalDistance() {
        var total = 0L;
        for (var distance : distances) {
            total += distance;
        }
        return total;
    }

    Solution toSolution() {
        final var out = new ArrayList<Route>(routes.size());
        for (var v = 0; v < routes.size(); v++) {
            final var route = routes.get(v);
            if (route.length == 0) {
                continue; // a vehicle that never left the depot is not part of the answer
            }
            out.add(new Route(
                    vehicles.get(v).id(),
                    route,
                    distances[v],
                    RouteCalculator.duration(route, route.length, vehicles.get(v), instance)));
        }
        return new Solution(out, unassigned, totalDistance());
    }
}
