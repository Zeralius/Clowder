package dev.zeralius.clowder.planning.solver;

/**
 * Moves a single stop from one route to another.
 *
 * <p>The operator that reduces vehicle count: it is the only one here that moves a stop
 * <em>between</em> routes, so it is the only one that can empty a route. A move that empties its
 * source is accepted even when it lengthens total distance, because vehicle count outranks distance
 * in the objective. Forgetting that is the most common reason a solver plateaus one vehicle above
 * best-known.
 *
 * <p>The mirror rule matters just as much: a stop is never moved <em>into</em> an empty route. That
 * would put a vehicle on the road, which worsens the first-ranked term no matter how much distance it
 * saves — and on a non-metric matrix it is exactly the move that undoes a route-emptying one, so
 * without this guard the two ping-pong until the deadline and the answer depends on where the clock
 * stopped.
 */
final class RelocateOperator implements LocalSearchOperator {

    @Override
    public String name() {
        return "relocate";
    }

    @Override
    public boolean improve(SolutionState state) {
        for (var from = 0; from < state.routeCount(); from++) {
            final var source = state.route(from);
            if (source.length == 0) {
                continue;
            }

            for (var at = 0; at < source.length; at++) {
                final var moved = source[at];
                final var shortened = RouteCalculator.withRemoval(source, source.length, at);

                if (!state.isFeasible(from, shortened)) {
                    continue; // removal cannot break feasibility today, but do not assume it
                }
                final var sourceDelta = state.distanceOf(from, shortened) - state.distance(from);
                final var emptiesSource = shortened.length == 0;

                for (var to = 0; to < state.routeCount(); to++) {
                    if (to == from) {
                        continue;
                    }
                    final var target = state.route(to);
                    if (target.length == 0) {
                        continue; // never put a vehicle on the road that was not already out
                    }

                    for (var insertAt = 0; insertAt <= target.length; insertAt++) {
                        final var lengthened =
                                RouteCalculator.withInsertion(target, target.length, insertAt, moved);

                        final var targetDelta = state.distanceOf(to, lengthened) - state.distance(to);
                        final var totalDelta = sourceDelta + targetDelta;

                        // Losing a vehicle beats any distance penalty: it is the higher-ranked term.
                        if (!emptiesSource && totalDelta >= 0) {
                            continue;
                        }
                        if (!state.isFeasible(to, lengthened)) {
                            continue;
                        }

                        state.replace(from, shortened);
                        state.replace(to, lengthened);
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
