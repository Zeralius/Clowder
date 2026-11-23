package dev.zeralius.clowder.planning.solver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Empties a whole route at once by reinserting every one of its stops into the other routes.
 *
 * <p>This exists because {@link RelocateOperator} cannot do it. Relocate moves one stop at a time
 * and will only accept a move that either shortens the total or empties the source outright.
 * Emptying a route of three stops needs three accepted moves, and the first two empty nothing, so
 * they are judged on distance alone. Pulling a stop out of an already short route and pushing it
 * into another one almost always makes the total longer, so those two moves are rejected and the
 * route survives. Measured on the Solomon set, that left about one extra vehicle per instance.
 *
 * <p>So the move here is all-or-nothing: find homes for every stop, or change nothing. Distance is
 * allowed to get worse, because vehicle count is the first term of the objective and outranks it.
 *
 * <p>Routes are tried shortest first, which is both the cheapest to empty and the most likely to
 * succeed. Everything iterates in a fixed order and ties break on the lowest index, so the result
 * does not depend on encounter order.
 */
final class RouteEliminationOperator implements LocalSearchOperator {

    @Override
    public String name() {
        return "route-elimination";
    }

    @Override
    public boolean improve(SolutionState state) {
        for (var victim : candidatesShortestFirst(state)) {
            final var absorbed = tryToAbsorb(state, victim);
            if (absorbed != null) {
                absorbed.applyTo(state, victim);
                return true;
            }
        }
        return false;
    }

    /** Non-empty routes, fewest stops first, falling back to the lowest index so ties are stable. */
    private static List<Integer> candidatesShortestFirst(SolutionState state) {
        final var candidates = new ArrayList<Integer>();
        for (var v = 0; v < state.routeCount(); v++) {
            if (state.route(v).length > 0) {
                candidates.add(v);
            }
        }
        candidates.sort(
                Comparator.<Integer>comparingInt(v -> state.route(v).length).thenComparingInt(v -> v));
        return candidates;
    }

    /**
     * Tries to place every stop of {@code victim} into some other route.
     *
     * @return the rearranged routes, or {@code null} if even one stop had nowhere feasible to go
     */
    private static Placement tryToAbsorb(SolutionState state, int victim) {
        // Work on copies so a failed attempt costs nothing. Only a successful one touches the state.
        final var working = new int[state.routeCount()][];
        for (var v = 0; v < state.routeCount(); v++) {
            working[v] = state.route(v);
        }
        final var touched = new boolean[state.routeCount()];

        for (var stop : state.route(victim)) {
            if (!place(state, working, touched, victim, stop)) {
                return null;
            }
        }
        return new Placement(working, touched);
    }

    /** Puts one stop in its cheapest feasible slot across every other non-empty route. */
    private static boolean place(SolutionState state, int[][] working, boolean[] touched, int victim, int stop) {
        var bestRoute = -1;
        var bestAt = -1;
        var bestDelta = Long.MAX_VALUE;

        for (var target = 0; target < working.length; target++) {
            if (target == victim || working[target].length == 0) {
                continue; // never start a new vehicle to retire another one
            }
            final var route = working[target];
            final var before = state.distanceOf(target, route);

            for (var at = 0; at <= route.length; at++) {
                final var candidate = RouteCalculator.withInsertion(route, route.length, at, stop);
                if (!state.isFeasible(target, candidate)) {
                    continue;
                }
                final var delta = state.distanceOf(target, candidate) - before;
                // Strictly cheaper keeps the lowest route and position on a tie.
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

        working[bestRoute] = RouteCalculator.withInsertion(working[bestRoute], working[bestRoute].length, bestAt, stop);
        touched[bestRoute] = true;
        return true;
    }

    private record Placement(int[][] routes, boolean[] touched) {

        void applyTo(SolutionState state, int victim) {
            for (var v = 0; v < routes.length; v++) {
                if (touched[v]) {
                    state.replace(v, routes[v]);
                }
            }
            state.replace(victim, new int[0]);
        }
    }
}
