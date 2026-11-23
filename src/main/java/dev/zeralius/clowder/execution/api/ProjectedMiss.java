package dev.zeralius.clowder.execution.api;

import java.time.Instant;

/**
 * A stop the vehicle is now expected to reach too late.
 *
 * @param projectedArrival where the remaining route lands given the delay so far
 * @param windowEnd the latest service start the customer was promised
 * @param lateByMinutes how far past the window that arrival falls
 */
public record ProjectedMiss(
        Long consignmentId, String reference, Instant projectedArrival, Instant windowEnd, int lateByMinutes) {}
