package dev.zeralius.clowder.execution.domain;

import dev.zeralius.clowder.common.error.ConflictException;
import dev.zeralius.clowder.consignment.ConsignmentFacade;
import dev.zeralius.clowder.execution.api.EventResponse;
import dev.zeralius.clowder.execution.api.RecordEventRequest;
import dev.zeralius.clowder.execution.persistence.DriverEventRepository;
import dev.zeralius.clowder.planning.PlanningFacade;
import dev.zeralius.clowder.planning.plan.api.PlanResponse;
import dev.zeralius.clowder.planning.plan.api.PlanStopResponse;
import java.util.Set;
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

    private static final Set<String> ACCEPTS_EVENTS = Set.of("PUBLISHED", "SUPERSEDED");

    private final DriverEventRepository events;
    private final PlanningFacade plans;
    private final ConsignmentFacade consignments;
    private final ExecutionStateUpdater state;

    public EventIngestionService(
            DriverEventRepository events,
            PlanningFacade plans,
            ConsignmentFacade consignments,
            ExecutionStateUpdater state) {
        this.events = events;
        this.plans = plans;
        this.consignments = consignments;
        this.state = state;
    }

    @Transactional
    public EventResponse record(Long planId, RecordEventRequest request) {
        final var replay = events.findByIdempotencyKey(request.idempotencyKey());
        if (replay.isPresent()) {
            return EventResponse.from(requireSamePlan(replay.get(), planId), true);
        }

        // Throws NotFoundException when there is no such plan, which the handler turns into a 404.
        final var plan = plans.findPlan(planId);
        requireAcceptsEvents(plan);

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

        applyToConsignments(plan, event);

        // The materialised projection is refreshed in this transaction, not after it. A log the cache
        // has not seen is a cache that is quietly wrong, and rolling both back together is cheaper
        // than any reconciliation that would otherwise be needed later.
        state.refresh(planId, plan.stops());

        return EventResponse.from(event, false);
    }

    /**
     * A driver works from a published plan. A superseded one is accepted too, and deliberately: a
     * driver halfway through v1 when the dispatcher publishes v2 is still delivering real parcels, and
     * dropping those reports to keep the model tidy would lose the only record of what happened.
     * A draft has never been given to anyone, and a cancelled plan was called off.
     */
    private static void requireAcceptsEvents(PlanResponse plan) {
        if (!ACCEPTS_EVENTS.contains(plan.status().name())) {
            throw new ConflictException(
                    "Plan %d is %s, so it has no driver to report against".formatted(plan.id(), plan.status()));
        }
    }

    /**
     * The order lifecycle, moved by what the driver reported. The event log stays the record of what
     * happened; this is the consequence for the consignment, which the rest of the business reads.
     *
     * <p>Only the events that mean something to an order are handled. An {@code ARRIVED} says where
     * the van is, not what became of the parcel.
     */
    private void applyToConsignments(PlanResponse plan, DriverEvent event) {
        switch (event.getEventType()) {
            case DEPARTED_DEPOT ->
                consignments.markDispatched(plan.stops().stream()
                        .map(PlanStopResponse::consignmentId)
                        .toList());
            case DELIVERED -> consignments.markDelivered(consignmentOf(plan, event.getPlanStopId()));
            case FAILED -> consignments.markFailed(consignmentOf(plan, event.getPlanStopId()));
            case ARRIVED, DEPARTED, RETURNED_DEPOT -> {
                // Nothing to say about the order itself.
            }
        }
    }

    private static Long consignmentOf(PlanResponse plan, Long planStopId) {
        return plan.stops().stream()
                .filter(stop -> planStopId.equals(stop.id()))
                .map(PlanStopResponse::consignmentId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Stop %d vanished from plan %d mid-transaction".formatted(planStopId, plan.id())));
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

    private static void requireStopOnPlan(dev.zeralius.clowder.planning.plan.api.PlanResponse plan, Long planStopId) {
        if (planStopId == null) {
            throw new ConflictException("A stop-scoped event needs a planStopId");
        }
        final var onPlan = plan.stops().stream().anyMatch(stop -> planStopId.equals(stop.id()));
        if (!onPlan) {
            throw new ConflictException("Stop %d is not on plan %d".formatted(planStopId, plan.id()));
        }
    }
}
