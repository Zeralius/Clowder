package dev.zeralius.clowder.execution.api;

import dev.zeralius.clowder.execution.domain.StopState;
import java.time.Instant;
import java.util.List;

/**
 * Plan against reality for one plan.
 *
 * @param currentDelayMinutes how late the last completed stop ran; negative means early
 * @param projectedWindowMisses stops the remaining route can no longer reach in time, from re-running
 *     the feasibility engine over what is left
 */
public record TrackingResponse(
        Long planId,
        int stopsCompleted,
        int stopsRemaining,
        int currentDelayMinutes,
        Instant lastKnownDeparture,
        List<StopState> stops,
        List<ProjectedMiss> projectedWindowMisses) {}
