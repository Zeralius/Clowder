package dev.zeralius.clowder.planning.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Kicks a converged solution out of its local optimum: take a handful of stops out, then put them
 * back wherever they fit most cheaply.
 *
 * <p>Measured on the Solomon set, the operators converge in about 20 ms and then have nothing left
 * to try, so a 5 second budget sits almost entirely unused. They also leave routes using 86% of
 * their shift on average while only 65% of their weight capacity, which says the binding constraint
 * is time rather than load. A route that wastes shift time on a poor ordering cannot absorb another
 * route's stops, so it keeps a vehicle on the road. Both facts point the same way: spend the
 * remaining budget looking for a different arrangement rather than stopping at the first local
 * optimum.
 *
 * <p>Stops are never lost. If any removed stop has nowhere feasible to go, the whole perturbation is
 * abandoned and the caller's state is untouched.
 *
 * <p>The randomness is drawn from a {@link Random} the caller seeds from {@code SolverConfig}, so a
 * run is still reproducible. There is no unseeded randomness anywhere in the solver.
 */
final class RuinAndRecreate {

    private RuinAndRecreate() {}

    /**
     * Removes {@code count} stops at random and reinserts each at its cheapest feasible position.
     *
     * @return whether the state was changed
     */
    static boolean perturb(SolutionState state, Random random, int count) {
        final var occupied = occupiedRoutes(state);
        if (occupied.size() < 2) {
            return false; // nothing to rearrange between
        }

        final var working = new int[state.routeCount()][];
        for (var v = 0; v < state.routeCount(); v++) {
            working[v] = state.route(v);
        }

        final var removed = ruin(working, occupied, random, count);
        if (removed.isEmpty()) {
            return false;
        }

        if (!recreate(state, working, removed)) {
            return false; // could not put everything back, so change nothing
        }

        for (var v = 0; v < working.length; v++) {
            if (working[v] != state.route(v)) {
                state.replace(v, working[v]);
            }
        }
        return true;
    }

    private static List<Integer> occupiedRoutes(SolutionState state) {
        final var occupied = new ArrayList<Integer>();
        for (var v = 0; v < state.routeCount(); v++) {
            if (state.route(v).length > 0) {
                occupied.add(v);
            }
        }
        return occupied;
    }

    /** Pulls stops out one at a time, each from a route that still has something in it. */
    private static List<Integer> ruin(int[][] working, List<Integer> occupied, Random random, int count) {
        final var removed = new ArrayList<Integer>(count);

        for (var i = 0; i < count; i++) {
            final var candidates = new ArrayList<Integer>();
            for (var v : occupied) {
                if (working[v].length > 0) {
                    candidates.add(v);
                }
            }
            if (candidates.isEmpty()) {
                break;
            }
            final var route = candidates.get(random.nextInt(candidates.size()));
            final var at = random.nextInt(working[route].length);
            removed.add(working[route][at]);
            working[route] = RouteCalculator.withRemoval(working[route], working[route].length, at);
        }
        return removed;
    }

    /**
     * Puts every removed stop back at its cheapest feasible position, in the order they came out.
     *
     * <p>Only routes that still have stops are considered. Opening a fresh route here would add a
     * vehicle, and vehicle count is the first term of the objective, so it would never be accepted.
     */
    private static boolean recreate(SolutionState state, int[][] working, List<Integer> removed) {
        for (var stop : removed) {
            var bestRoute = -1;
            var bestAt = -1;
            var bestDelta = Long.MAX_VALUE;

            for (var target = 0; target < working.length; target++) {
                if (working[target].length == 0) {
                    continue;
                }
                final var route = working[target];
                final var before = state.distanceOf(target, route);

                for (var at = 0; at <= route.length; at++) {
                    final var candidate = RouteCalculator.withInsertion(route, route.length, at, stop);
                    if (!state.isFeasible(target, candidate)) {
                        continue;
                    }
                    final var delta = state.distanceOf(target, candidate) - before;
                    if (delta < bestDelta) {
                        bestDelta = delta;
                        bestRoute = target;
                        bestAt = at;
                    }
                }
            }

            if (bestRoute < 0) {
                return false;
            }
            working[bestRoute] =
                    RouteCalculator.withInsertion(working[bestRoute], working[bestRoute].length, bestAt, stop);
        }
        return true;
    }
}
