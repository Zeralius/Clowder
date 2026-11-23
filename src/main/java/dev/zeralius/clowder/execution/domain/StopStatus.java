package dev.zeralius.clowder.execution.domain;

/**
 * Projected state of one stop.
 *
 * <p>{@code rank} is what makes the fold tolerant of out-of-order arrival: a status can only move up,
 * so an ARRIVED that turns up after a DELIVERED is ignored instead of corrupting the state. That is
 * precedence rather than last-write-wins, and it is a deliberate choice, not an accident of ordering.
 */
public enum StopStatus {
    PENDING(0),
    ARRIVED(1),
    DELIVERED(2),
    FAILED(2);

    private final int rank;

    StopStatus(int rank) {
        this.rank = rank;
    }

    public boolean outranks(StopStatus other) {
        return this.rank > other.rank;
    }

    public boolean isComplete() {
        return this == DELIVERED || this == FAILED;
    }
}
