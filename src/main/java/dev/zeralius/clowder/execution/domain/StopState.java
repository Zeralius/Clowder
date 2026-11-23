package dev.zeralius.clowder.execution.domain;

import java.time.Instant;

/** Projected state of a single stop, derived entirely from the event log. */
public record StopState(
        Long planStopId,
        Long consignmentId,
        String reference,
        StopStatus status,
        Instant actualArrival,
        Instant actualDeparture,
        FailureReason failureReason) {

    static StopState pending(Long planStopId, Long consignmentId, String reference) {
        return new StopState(planStopId, consignmentId, reference, StopStatus.PENDING, null, null, null);
    }

    /** Precedence, not last-write-wins: a late ARRIVED must not undo a DELIVERED. */
    StopState withStatus(StopStatus newStatus, FailureReason reason) {
        return newStatus.outranks(status)
                ? new StopState(planStopId, consignmentId, reference, newStatus, actualArrival, actualDeparture, reason)
                : this;
    }

    /** First arrival wins. A second ARRIVED is a retry or a re-approach, not a new fact. */
    StopState withArrival(Instant at) {
        return actualArrival == null
                ? new StopState(planStopId, consignmentId, reference, status, at, actualDeparture, failureReason)
                : this;
    }

    StopState withDeparture(Instant at) {
        return new StopState(planStopId, consignmentId, reference, status, actualArrival, at, failureReason);
    }
}
