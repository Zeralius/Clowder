package dev.zeralius.clowder.execution.domain;

import dev.zeralius.clowder.common.error.ConflictException;
import dev.zeralius.clowder.execution.api.EventResponse;
import dev.zeralius.clowder.execution.api.RecordEventRequest;
import dev.zeralius.clowder.execution.persistence.DriverEventRepository;
import dev.zeralius.clowder.planning.PlanningFacade;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent ingestion. Replaying an event returns the response the first call produced.
 *
 * <p><b>What idempotency means here.</b> The client generates a key per event and retries with the
 * same key until it gets an answer, so a replay must return the original result — not a duplicate
 * insert, and not a 409. A 409 would be right if the key meant "this is new". It does not; it means
 * "this is the same thing I already told you".
 *
 * <p><b>Two layers, deliberately.</b> The lookup is the behaviour and the unique constraint is the
 * backstop for two retries landing in the same millisecond. Catching the violation and re-reading is
 * not belt-and-braces: without it the race needs a lock.
 *
 * <p>No response body is stored, because {@link EventResponse} is a pure function of the persisted
 * row — re-mapping it <em>is</em> returning the original. The day a response carries something not
 * derivable from the row, a generated token say, the body has to be stored instead.
 */
@Service
public class EventIngestionService {

    private final DriverEventRepository events;
    private final PlanningFacade plans;

    public EventIngestionService(DriverEventRepository events, PlanningFacade plans) {
        this.events = events;
        this.plans = plans;
    }

    @Transactional
    public EventResponse record(Long planId, RecordEventRequest request) {
        final var replay = events.findByIdempotencyKey(request.idempotencyKey());
        if (replay.isPresent()) {
            return EventResponse.from(requireSamePlan(replay.get(), planId), true);
        }

        // Throws NotFoundException when there is no such plan, which the handler turns into a 404.
        final var plan = plans.findPlan(planId);

        if (request.type().isStopScoped()) {
            requireStopOnPlan(plan, request.planStopId());
        }

        final var event = new DriverEvent(
                request.idempotencyKey(),
                planId,
                request.type().isStopScoped() ? request.planStopId() : null,
                request.type(),
                request.occurredAt(),
                request.latitude(),
                request.longitude(),
                request.failureReason());

        try {
            // saveAndFlush, not save: the constraint has to fire inside this method, where the catch
            // below can see it. The same reason PlanPublisher flushes explicitly.
            events.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            // Two retries raced. The winner row is the answer, so re-read and return it.
            return EventResponse.from(
                    events.findByIdempotencyKey(request.idempotencyKey()).orElseThrow(() -> e), true);
        }

        return EventResponse.from(event, false);
    }

    /**
     * A key is unique across the whole log, so the same key arriving under a different plan is a
     * client bug rather than a retry, and answering with the other plan event would hide it.
     */
    private static DriverEvent requireSamePlan(DriverEvent stored, Long planId) {
        if (!stored.getPlanId().equals(planId)) {
            throw new ConflictException("Idempotency key %s already belongs to plan %d"
                    .formatted(stored.getIdempotencyKey(), stored.getPlanId()));
        }
        return stored;
    }

    private static void requireStopOnPlan(
            dev.zeralius.clowder.planning.plan.api.PlanResponse plan, Long planStopId) {
        if (planStopId == null) {
            throw new ConflictException("A stop-scoped event needs a planStopId");
        }
        final var onPlan = plan.stops().stream().anyMatch(stop -> planStopId.equals(stop.id()));
        if (!onPlan) {
            throw new ConflictException("Stop %d is not on plan %d".formatted(planStopId, plan.id()));
        }
    }
}
