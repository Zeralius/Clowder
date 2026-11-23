package dev.zeralius.clowder.planning.plan.api;

import java.time.Instant;

/**
 * One visit, as it was planned. Every field is a snapshot taken when the plan was assembled, so this
 * is what the dispatcher was told rather than what the consignment says today.
 *
 * @param waitMinutes time spent waiting for the window to open — normal, never a violation
 * @param loadAfter what remains on the vehicle once this stop has been served
 */
public record PlanStopResponse(
        int sequenceNo,
        Long consignmentId,
        String reference,
        String addressLine,
        Instant plannedArrival,
        Instant plannedServiceStart,
        Instant plannedDeparture,
        int waitMinutes,
        long cumulativeDistance,
        long loadAfter) {}
