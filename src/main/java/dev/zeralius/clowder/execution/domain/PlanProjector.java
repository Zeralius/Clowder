package dev.zeralius.clowder.execution.domain;

import dev.zeralius.clowder.planning.plan.api.PlanStopResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Folds an event log into current state.
 *
 * <p>A pure function: it takes stops and events, returns a projection, and touches nothing else — no
 * repository, no {@code Instant.now()}, no Spring. That is what makes the rebuild test meaningful. If
 * the projector could reach outside its arguments, replaying the log would prove nothing.
 *
 * <p>It folds over {@link PlanStopResponse} rather than the planning entity, which keeps the module
 * boundary intact and has a practical benefit: the DTO carries a real stop id, so the projector can be
 * tested without a database.
 */
public final class PlanProjector {

    public PlanProjection project(Long planId, List<PlanStopResponse> planStops, List<DriverEvent> events) {
        // LinkedHashMap: insertion-ordered, so the output follows plan sequence rather than a hash.
        final var states = new LinkedHashMap<Long, StopState>();
        planStops.stream()
                .sorted(Comparator.comparingInt(PlanStopResponse::sequenceNo))
                .forEach(stop ->
                        states.put(stop.id(), StopState.pending(stop.id(), stop.consignmentId(), stop.reference())));

        Instant lastKnownDeparture = null;
        Long lastCompletedStopId = null;

        for (final var event : sortedByDeviceTime(events)) {
            if (!event.getEventType().isStopScoped()) {
                if (event.getEventType() == DriverEventType.DEPARTED_DEPOT) {
                    lastKnownDeparture = event.getOccurredAt();
                }
                continue;
            }

            final var current = states.get(event.getPlanStopId());
            if (current == null) {
                // An event naming a stop that is not on this plan. Ignored rather than rejected: the
                // log is append-only and a bad reference must not wedge the rest of the day.
                continue;
            }

            states.put(event.getPlanStopId(), apply(current, event));

            if (event.getEventType().isTerminalForStop()) {
                lastKnownDeparture = event.getOccurredAt();
                lastCompletedStopId = event.getPlanStopId();
            }
        }

        final var stops = new ArrayList<>(states.values());
        final var completed =
                (int) stops.stream().filter(stop -> stop.status().isComplete()).count();

        return new PlanProjection(
                planId,
                completed,
                stops.size() - completed,
                delayAt(planStops, lastCompletedStopId, lastKnownDeparture),
                lastKnownDeparture,
                lastCompletedStopId,
                stops);
    }

    private static StopState apply(StopState current, DriverEvent event) {
        return switch (event.getEventType()) {
            case ARRIVED -> current.withStatus(StopStatus.ARRIVED, null).withArrival(event.getOccurredAt());
            case DELIVERED -> current.withStatus(StopStatus.DELIVERED, null).withDeparture(event.getOccurredAt());
            case FAILED ->
                current.withStatus(StopStatus.FAILED, event.getFailureReason()).withDeparture(event.getOccurredAt());
            case DEPARTED -> current.withDeparture(event.getOccurredAt());
            case DEPARTED_DEPOT, RETURNED_DEPOT -> current;
        };
    }

    /**
     * Device time first, then id. Arrival order is deliberately not consulted: a burst upload after a
     * tunnel delivers six events at once and must fold to the same answer as six timely ones.
     */
    private static List<DriverEvent> sortedByDeviceTime(List<DriverEvent> events) {
        return events.stream()
                .sorted(Comparator.comparing(DriverEvent::getOccurredAt)
                        .thenComparing(DriverEvent::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** How far behind the last completed stop ran, against the time frozen into the plan at publish. */
    private static int delayAt(List<PlanStopResponse> planStops, Long stopId, Instant actualDeparture) {
        if (stopId == null || actualDeparture == null) {
            return 0;
        }
        return planStops.stream()
                .filter(stop -> stopId.equals(stop.id()))
                .findFirst()
                .map(stop -> (int) Duration.between(stop.plannedDeparture(), actualDeparture)
                        .toMinutes())
                .orElse(0);
    }
}
