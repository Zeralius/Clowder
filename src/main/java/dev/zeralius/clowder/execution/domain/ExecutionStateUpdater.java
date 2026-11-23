package dev.zeralius.clowder.execution.domain;

import dev.zeralius.clowder.execution.persistence.DriverEventRepository;
import dev.zeralius.clowder.execution.persistence.PlanExecutionStateRepository;
import dev.zeralius.clowder.execution.persistence.PlanStopStateRepository;
import dev.zeralius.clowder.planning.PlanningFacade;
import dev.zeralius.clowder.planning.plan.api.PlanStopResponse;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Keeps the materialised projection in step with the log.
 *
 * <p><b>Recomputed, not incrementally patched.</b> Every refresh folds the whole of one plan log and
 * overwrites what was there. That is more work than applying a delta and it is the right trade: a
 * plan log is tens of events, while an incremental fold would have to get every out-of-order and
 * duplicate case right a second time, in a different place, with the precedence rules duplicated. The
 * projector already does that correctly and is tested; this just stores its answer.
 *
 * <p>So the cache cannot drift from the log by construction. {@link #rebuild} exists for the case
 * where it did anyway — a schema change, a restore, a bug — and is the same code path the refresh
 * takes, which is why there is no separate rebuild to keep correct.
 */
@Service
public class ExecutionStateUpdater {

    private final PlanningFacade plans;
    private final DriverEventRepository events;
    private final PlanExecutionStateRepository planState;
    private final PlanStopStateRepository stopState;
    private final PlanProjector projector = new PlanProjector();
    private final Clock clock;

    public ExecutionStateUpdater(
            PlanningFacade plans,
            DriverEventRepository events,
            PlanExecutionStateRepository planState,
            PlanStopStateRepository stopState,
            Clock clock) {
        this.plans = plans;
        this.events = events;
        this.planState = planState;
        this.stopState = stopState;
        this.clock = clock;
    }

    /**
     * Called after an event is accepted, inside the caller transaction. If this write fails the event
     * is not recorded either, which is the behaviour worth having: a log the cache has not seen is a
     * cache that is silently wrong.
     *
     * <p>No {@code @Transactional} here, deliberately. {@link #rebuild} calls this method on the same
     * instance, and a call from one method to another in the same class never reaches the proxy, so an
     * annotation would be decoration that reads like a guarantee. The check below is the guarantee.
     */
    public PlanProjection refresh(Long planId, List<PlanStopResponse> stops) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("refresh must run inside the transaction that wrote the event");
        }

        final var log = events.findByPlanIdOrderByOccurredAtAscIdAsc(planId);
        final var projection = projector.project(planId, stops, log);

        final var existing = new HashMap<Long, PlanStopState>();
        for (final var row : stopState.findByPlanIdOrderByPlanStopIdAsc(planId)) {
            existing.put(row.getPlanStopId(), row);
        }

        for (final var state : projection.stops()) {
            final var row = existing.remove(state.planStopId());
            if (row == null) {
                stopState.save(new PlanStopState(planId, state));
            } else {
                row.replaceWith(state);
            }
        }

        // Stops that are no longer on the plan. A published plan is immutable, so this only happens
        // after a rebuild against a different plan version, but leaving orphans would make the
        // rebuild test pass while the table slowly filled with rows nothing reads.
        existing.values().forEach(stopState::delete);

        // Filled in before the insert, not after: every column on this row is NOT NULL, so saving an
        // empty one first and populating it afterwards fails on the way in.
        final var header = planState.findById(planId).orElseGet(() -> new PlanExecutionState(planId));
        header.replaceWith(projection, log.size(), clock.instant());
        planState.save(header);

        return projection;
    }

    /** The same path, taken deliberately: drop what is there and fold the log again. */
    @Transactional
    public PlanProjection rebuild(Long planId) {
        final var plan = plans.findPlan(planId);
        stopState.deleteByPlanId(planId);
        stopState.flush();
        return refresh(planId, plan.stops());
    }
}
