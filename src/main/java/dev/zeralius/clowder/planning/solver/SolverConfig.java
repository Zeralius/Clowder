package dev.zeralius.clowder.planning.solver;

import java.time.Duration;

/**
 * Everything tunable, in one place, logged with every result so a benchmark row can be reproduced.
 *
 * @param seed fixed for determinism; there is no unseeded randomness anywhere in the solver
 * @param budget wall-clock limit for local search — a time budget, not an iteration count, because
 *     predictable wall-clock behaviour is what a caller can actually reason about
 * @param insertionAlpha Solomon I1 weight between added distance (1.0) and delayed service (0.0)
 * @param insertionLambda Solomon I1 weight on distance from the depot; 1 and 2 are the usual values
 * @param maxNoImprovement consecutive non-improving local-search passes before stopping early
 */
public record SolverConfig(
        long seed, Duration budget, double insertionAlpha, double insertionLambda, int maxNoImprovement) {

    public SolverConfig {
        if (budget == null || budget.isNegative() || budget.isZero()) {
            throw new IllegalArgumentException("budget must be positive");
        }
        if (insertionAlpha < 0 || insertionAlpha > 1) {
            throw new IllegalArgumentException("insertionAlpha must be in [0, 1]: " + insertionAlpha);
        }
        if (maxNoImprovement < 1) {
            throw new IllegalArgumentException("maxNoImprovement must be positive");
        }
    }

    /** Sensible starting point; tune against the benchmark set, not against intuition. */
    public static SolverConfig defaults() {
        return new SolverConfig(20260105L, Duration.ofSeconds(5), 1.0d, 2.0d, 50);
    }

    public SolverConfig withBudget(Duration newBudget) {
        return new SolverConfig(seed, newBudget, insertionAlpha, insertionLambda, maxNoImprovement);
    }
}
