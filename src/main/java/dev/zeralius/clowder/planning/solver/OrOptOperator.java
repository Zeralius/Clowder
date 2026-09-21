package dev.zeralius.clowder.planning.solver;

/**
 * Moves a chain of 1 to 3 consecutive stops to a different position in the same route, without
 * reversing it — what 2-opt cannot express. One to three is the classic range: long enough to move a
 * small cluster that belongs elsewhere, short enough that the neighbourhood stays cheap.
 *
 * <p>Check whether it earns its runtime on your instances before keeping it in the default operator
 * list; it is here because it is twenty lines and composes cleanly.
 */
final class OrOptOperator implements LocalSearchOperator {

    private static final int MAX_CHAIN = 3;

    @Override
    public String name() {
        return "or-opt";
    }

    @Override
    public boolean improve(SolutionState state) {
        for (var v = 0; v < state.routeCount(); v++) {
            final var route = state.route(v);
            final var n = route.length;
            if (n < 3) {
                continue;
            }

            for (var length = 1; length <= Math.min(MAX_CHAIN, n - 1); length++) {
                for (var start = 0; start + length <= n; start++) {
                    final var withoutChain = removeChain(route, start, length);

                    for (var insertAt = 0; insertAt <= withoutChain.length; insertAt++) {
                        if (insertAt == start) {
                            continue; // putting it back where it came from
                        }
                        final var candidate = insertChain(withoutChain, insertAt, route, start, length);

                        if (state.distanceOf(v, candidate) >= state.distance(v)) {
                            continue;
                        }
                        if (state.isFeasible(v, candidate)) {
                            state.replace(v, candidate);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static int[] removeChain(int[] route, int start, int length) {
        final var out = new int[route.length - length];
        System.arraycopy(route, 0, out, 0, start);
        System.arraycopy(route, start + length, out, start, route.length - start - length);
        return out;
    }

    private static int[] insertChain(int[] base, int at, int[] source, int chainStart, int chainLength) {
        final var out = new int[base.length + chainLength];
        System.arraycopy(base, 0, out, 0, at);
        System.arraycopy(source, chainStart, out, at, chainLength);
        System.arraycopy(base, at, out, at + chainLength, base.length - at);
        return out;
    }
}
