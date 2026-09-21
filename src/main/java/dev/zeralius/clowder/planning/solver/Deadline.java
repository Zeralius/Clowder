package dev.zeralius.clowder.planning.solver;

import java.time.Duration;

/**
 * A monotonic wall-clock budget. {@code System.nanoTime} because it is immune to clock adjustments.
 *
 * <p>A time budget rather than an iteration count: a caller can reason about "five seconds", nobody
 * can reason about "twenty thousand iterations" without knowing the instance size.
 */
final class Deadline {

    private final long expiresAtNanos;

    private Deadline(long expiresAtNanos) {
        this.expiresAtNanos = expiresAtNanos;
    }

    static Deadline startingNow(Duration budget) {
        return new Deadline(System.nanoTime() + budget.toNanos());
    }

    boolean hasExpired() {
        return System.nanoTime() >= expiresAtNanos;
    }
}
