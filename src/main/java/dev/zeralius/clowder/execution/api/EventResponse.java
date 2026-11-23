package dev.zeralius.clowder.execution.api;

import dev.zeralius.clowder.execution.domain.DriverEvent;
import dev.zeralius.clowder.execution.domain.DriverEventType;
import dev.zeralius.clowder.execution.domain.FailureReason;
import java.time.Instant;

/**
 * @param replayed true when this call was a retry and the stored event was returned unchanged
 */
public record EventResponse(
        Long id,
        String idempotencyKey,
        Long planId,
        Long planStopId,
        DriverEventType type,
        Instant occurredAt,
        Instant receivedAt,
        FailureReason failureReason,
        boolean replayed) {

    public static EventResponse from(DriverEvent event, boolean replayed) {
        return new EventResponse(
                event.getId(),
                event.getIdempotencyKey(),
                event.getPlanId(),
                event.getPlanStopId(),
                event.getEventType(),
                event.getOccurredAt(),
                event.getReceivedAt(),
                event.getFailureReason(),
                replayed);
    }
}
