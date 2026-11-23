package dev.zeralius.clowder.execution.domain;

import java.time.Instant;
import java.util.List;

/**
 * The whole of a plan execution state, folded from its events.
 *
 * <p>Nothing here is stored. Stop status is a projection over the log, and if it were also kept on
 * {@code plan_stop} that column would be a cache — with the test that proves it can be rebuilt worth
 * more than the column itself.
 *
 * @param currentDelayMinutes how far behind the most recently completed stop ran against the time
 *     frozen into the plan; negative means early
 * @param lastKnownDeparture where the vehicle timeline stands, for projecting the remainder
 */
public record PlanProjection(
        Long planId,
        int completed,
        int remaining,
        int currentDelayMinutes,
        Instant lastKnownDeparture,
        Long lastCompletedStopId,
        List<StopState> stops) {

    public PlanProjection {
        stops = List.copyOf(stops);
    }

    public List<StopState> remainingStops() {
        return stops.stream().filter(stop -> !stop.status().isComplete()).toList();
    }

    public boolean isFinished() {
        return remaining == 0;
    }
}
