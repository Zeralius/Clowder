package dev.zeralius.clowder.planning.solver;

import dev.zeralius.clowder.planning.model.ProblemInstance;
import dev.zeralius.clowder.planning.model.VehicleSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Solomon's I1 sequential insertion heuristic — the heuristic the published results were built
 * against, which is what makes the gap column mean something.
 *
 * <p>Builds one route at a time: seed it with the unrouted customer furthest from the depot, then
 * repeatedly insert the customer with the best {@code c2} score at its best {@code c1} position.
 * When nothing fits, close the route and open another.
 *
 * <pre>
 * c11(i,u,j) = d(i,u) + d(u,j) − μ·d(i,j)        added distance
 * c12(i,u,j) = b_j(new) − b_j(old)               how much later j gets served
 * c1 (i,u,j) = α1·c11 + α2·c12                   with α2 = 1 − α1
 * c2 (u)     = λ·d(depot,u) − c1(best position)  maximised across customers
 * </pre>
 *
 * <p>{@code c2} deliberately prefers customers far from the depot: they are the expensive ones, and
 * taking them while the route is empty is worth more than a cheap nearby customer that will still
 * fit later.
 *
 * <p>Ties in both {@code c1} and {@code c2} break on the lowest position, never on encounter order.
 * Without that, two runs return different routes of identical cost and the determinism test fails
 * for a reason that takes a day to find.
 *
 * <p>Cost: a trial array and, for interior positions, a service-start scan per (customer, position)
 * pair. A few million operations for 100 customers — well under a second. Local search is the
 * bottleneck, not this; optimise there first, and only if measurement says so.
 */
final class InsertionConstructor {

    /** Solomon's μ: how much the removed direct edge offsets the detour. 1 is the standard value. */
    private static final double MU = 1.0d;

    private final ProblemInstance instance;
    private final SolverConfig config;

    InsertionConstructor(ProblemInstance instance, SolverConfig config) {
        this.instance = instance;
        this.config = config;
    }

    Solution construct() {
        final var stopCount = instance.stopCount();
        final var unrouted = new boolean[stopCount];
        Arrays.fill(unrouted, true);
        var remaining = stopCount;

        final var routes = new ArrayList<Route>();
        final var vehicles = instance.vehicles();
        var vehicleCursor = 0;

        while (remaining > 0 && vehicleCursor < vehicles.size()) {
            final var vehicle = vehicles.get(vehicleCursor);
            final var seed = chooseSeed(unrouted, vehicle);

            if (seed < 0) {
                break; // nothing left that any single-stop route can serve
            }

            var route = new int[] {seed};
            unrouted[seed] = false;
            remaining--;

            while (true) {
                final var choice = bestInsertion(route, unrouted, vehicle);
                if (choice == null) {
                    break;
                }
                route = RouteCalculator.withInsertion(route, route.length, choice.at(), choice.stop());
                unrouted[choice.stop()] = false;
                remaining--;
            }

            routes.add(toRoute(vehicle, route));
            vehicleCursor++;
        }

        return toSolution(routes, unrouted);
    }

    /**
     * The unrouted customer furthest from the depot that a route can actually serve on its own. Seeding
     * with the hardest customer leaves the easy ones to fill gaps later.
     */
    private int chooseSeed(boolean[] unrouted, VehicleSpec vehicle) {
        final var stops = instance.stops();
        final var matrix = instance.matrix();
        final var depot = vehicle.depotIndex();

        var best = -1;
        var bestDistance = Long.MIN_VALUE;

        for (var position = 0; position < unrouted.length; position++) {
            if (!unrouted[position]) {
                continue;
            }
            final var single = new int[] {position};
            if (!RouteCalculator.isFeasible(single, 1, vehicle, instance)) {
                continue;
            }
            final var distance = matrix.distance(depot, stops.get(position).index());
            // Strict > keeps the lowest position on a tie: deterministic.
            if (distance > bestDistance) {
                bestDistance = distance;
                best = position;
            }
        }
        return best;
    }

    private Insertion bestInsertion(int[] route, boolean[] unrouted, VehicleSpec vehicle) {
        final var length = route.length;
        final var serviceStarts = new int[length];
        RouteCalculator.serviceStarts(route, length, vehicle, instance, serviceStarts);
        // Once per route revision, not per candidate: the route does not change inside this scan.
        final var slack = ForwardTimeSlack.compute(route, length, vehicle, instance);

        final var stops = instance.stops();
        final var matrix = instance.matrix();
        final var depot = vehicle.depotIndex();
        final var alpha1 = config.insertionAlpha();
        final var alpha2 = 1.0d - alpha1;

        Insertion best = null;
        var bestC2 = Double.NEGATIVE_INFINITY;

        for (var candidate = 0; candidate < unrouted.length; candidate++) {
            if (!unrouted[candidate]) {
                continue;
            }

            var bestC1 = Double.POSITIVE_INFINITY;
            var bestAt = -1;

            for (var at = 0; at <= length; at++) {
                // Cheap rejection first: if the stop after the gap would be pushed past its slack, no
                // array copy and no scan. Slack rejects; the full scan below still confirms.
                if (at < length
                        && ForwardTimeSlack.pushOnNext(route, length, at, candidate, serviceStarts, vehicle, instance)
                                > slack[at]) {
                    continue;
                }

                final var trial = RouteCalculator.withInsertion(route, length, at, candidate);
                if (!RouteCalculator.isFeasible(trial, trial.length, vehicle, instance)) {
                    continue;
                }

                final var before = at == 0 ? depot : stops.get(route[at - 1]).index();
                final var after = at == length ? depot : stops.get(route[at]).index();
                final var inserted = stops.get(candidate).index();

                final double c11 = matrix.distance(before, inserted)
                        + matrix.distance(inserted, after)
                        - MU * matrix.distance(before, after);

                final double c12;
                if (at == length) {
                    c12 = 0d; // nothing after it to delay
                } else {
                    final var trialStarts = new int[trial.length];
                    RouteCalculator.serviceStarts(trial, trial.length, vehicle, instance, trialStarts);
                    c12 = trialStarts[at + 1] - (double) serviceStarts[at];
                }

                final var c1 = alpha1 * c11 + alpha2 * c12;

                // Strict < keeps the earliest position on a tie.
                if (c1 < bestC1) {
                    bestC1 = c1;
                    bestAt = at;
                }
            }

            if (bestAt < 0) {
                continue; // this customer fits nowhere in this route
            }

            final var c2 = config.insertionLambda()
                            * matrix.distance(depot, stops.get(candidate).index())
                    - bestC1;

            // Strict > keeps the lowest candidate position on a tie.
            if (c2 > bestC2) {
                bestC2 = c2;
                best = new Insertion(candidate, bestAt);
            }
        }

        return best;
    }

    private Route toRoute(VehicleSpec vehicle, int[] route) {
        return new Route(
                vehicle.id(),
                route,
                RouteCalculator.distance(route, route.length, vehicle, instance),
                RouteCalculator.duration(route, route.length, vehicle, instance));
    }

    private Solution toSolution(List<Route> routes, boolean[] unrouted) {
        var count = 0;
        for (var stillUnrouted : unrouted) {
            if (stillUnrouted) {
                count++;
            }
        }
        final var unassigned = new int[count];
        var cursor = 0;
        for (var position = 0; position < unrouted.length; position++) {
            if (unrouted[position]) {
                unassigned[cursor++] = position;
            }
        }

        var total = 0L;
        for (var route : routes) {
            total += route.distance();
        }

        return new Solution(routes, unassigned, total);
    }

    /** Which customer to insert, and where. */
    private record Insertion(int stop, int at) {}
}
