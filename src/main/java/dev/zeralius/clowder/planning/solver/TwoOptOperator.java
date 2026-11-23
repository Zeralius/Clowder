package dev.zeralius.clowder.planning.solver;

/**
 * Intra-route segment reversal. Classically removes a crossing: {@code a-b ... c-d} becomes
 * {@code a-c ... b-d}, and if the two edges crossed the new pair is shorter.
 *
 * <p><b>Assumes a symmetric distance matrix</b> for its O(1) delta: the reversed segment's internal
 * distance is unchanged, so {@code delta = d(a,c) + d(b,d) - d(a,b) - d(c,d)}. True for Euclidean and
 * haversine, false for a road network where one-way streets make {@code d(x,y) != d(y,x)}. When M6
 * adds the OSRM provider, either recompute the candidate's full distance here or replace this with
 * inter-route 2-opt*, which reverses nothing.
 *
 * <p>Feasibility still needs a full scan: distance is unchanged inside the segment, the schedule is
 * not. Reversal is cheap in distance and expensive in feasibility — the opposite of the usual
 * intuition.
 */
final class TwoOptOperator implements LocalSearchOperator {

    @Override
    public String name() {
        return "2-opt";
    }

    @Override
    public boolean improve(SolutionState state) {
        final var instance = state.instance();
        final var matrix = instance.matrix();
        final var stops = instance.stops();

        for (var v = 0; v < state.routeCount(); v++) {
            final var route = state.route(v);
            final var n = route.length;
            if (n < 3) {
                continue; // nothing to reverse
            }
            final var depot = state.vehicle(v).depotIndex();

            for (var i = 0; i < n - 1; i++) {
                final var a = i == 0 ? depot : stops.get(route[i - 1]).index();
                final var b = stops.get(route[i]).index();

                for (var j = i + 1; j < n; j++) {
                    final var c = stops.get(route[j]).index();
                    final var d = j == n - 1 ? depot : stops.get(route[j + 1]).index();

                    final var delta = matrix.distance(a, c)
                            + matrix.distance(b, d)
                            - matrix.distance(a, b)
                            - matrix.distance(c, d);

                    if (delta >= 0) {
                        continue;
                    }

                    final var candidate = reversed(route, i, j);
                    if (state.isFeasible(v, candidate)) {
                        state.replace(v, candidate);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int[] reversed(int[] route, int from, int to) {
        final var out = route.clone();
        for (int left = from, right = to; left < right; left++, right--) {
            final var swap = out[left];
            out[left] = out[right];
            out[right] = swap;
        }
        return out;
    }
}
