package dev.zeralius.clowder.planning.plan.api;

import java.time.Instant;

/**
 * One visit, with the schedule M2's evaluator computed for it.
 *
 * @param waitMinutes time spent waiting for the window to open — normal, not a problem
 * @param loadAfterGrams what remains on the vehicle once this stop has been served
 */
public record PlannedStopResponse(
        int visitOrder,
        Long consignmentId,
        String reference,
        String addressLine,
        Instant arrival,
        Instant serviceStart,
        Instant departure,
        int waitMinutes,
        long loadAfterGrams) {}
