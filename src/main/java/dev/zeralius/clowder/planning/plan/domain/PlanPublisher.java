package dev.zeralius.clowder.planning.plan.domain;

import dev.zeralius.clowder.common.error.ConflictException;
import dev.zeralius.clowder.common.error.NotFoundException;
import dev.zeralius.clowder.consignment.ConsignmentFacade;
import dev.zeralius.clowder.planning.plan.api.PlanResponse;
import dev.zeralius.clowder.planning.plan.persistence.PlanRepository;
import dev.zeralius.clowder.security.CurrentUser;
import java.util.HashSet;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishing a plan supersedes whatever was published for that vehicle-day, atomically.
 *
 * <p>Correctness does not rest on this class. It rests on {@code uq_plan_published}, the partial
 * unique index: Postgres indexes only the rows where {@code status = 'PUBLISHED'}, so any number of
 * drafts and superseded versions may exist for a vehicle-day while at most one published row can.
 * Two dispatchers hitting publish in the same millisecond cannot both win, and neither can a bug in
 * this method or an {@code UPDATE} typed into psql at 2am. What this class adds is a good error
 * message when the index does its job.
 */
@Service
public class PlanPublisher {

    private final PlanRepository plans;
    private final ConsignmentFacade consignments;
    private final CurrentUser currentUser;

    public PlanPublisher(PlanRepository plans, ConsignmentFacade consignments, CurrentUser currentUser) {
        this.plans = plans;
        this.consignments = consignments;
        this.currentUser = currentUser;
    }

    @Transactional
    public PlanResponse publish(Long planId) {
        final var plan = plans.findWithStopsById(planId).orElseThrow(() -> new NotFoundException("Plan", planId));

        // Checked before anything is written, so a plan that was already published or cancelled fails
        // without first superseding the version that is legitimately live.
        if (!plan.getStatus().canTransitionTo(PlanStatus.PUBLISHED)) {
            throw new ConflictException("Plan %d cannot move from %s to PUBLISHED".formatted(planId, plan.getStatus()));
        }

        final var current = plans.findWithStopsByVehicleIdAndServiceDateAndStatus(
                plan.getVehicleId(), plan.getServiceDate(), PlanStatus.PUBLISHED);

        final var superseded = current.map(PlanPublisher::consignmentIdsOf).orElseGet(Set::of);

        if (current.isPresent()) {
            current.get().supersede();
            // Two flushes, not one. Hibernate is free to order the statements in a single flush as it
            // likes, and if the new row is written first there are briefly two published rows for the
            // vehicle-day — which the partial unique index correctly refuses. Taking the old row out
            // of the index before putting the new one in removes the question.
            plans.flush();
        }

        // Read before the write, so a publish is attributed to the caller that asked for it and not
        // to whatever the request thread happens to hold afterwards. Who may publish at all is a role
        // rule and is settled in the filter chain long before this method runs.
        plan.publish(currentUser.require().username());

        try {
            // Force the write now. Without this the index check happens at commit, after this method
            // has returned, and the catch below is dead code.
            plans.flush();
        } catch (DataIntegrityViolationException | OptimisticLockingFailureException e) {
            // Two publishes racing surface differently depending on where Postgres blocks: the loser
            // may hit the unique index, or may find the row it meant to supersede already changed.
            // Both mean the same thing to a caller, and both must roll back — after either one the
            // persistence context is in an undefined state, so there is nothing to recover.
            throw new ConflictException("A plan is already published for vehicle %d on %s"
                    .formatted(plan.getVehicleId(), plan.getServiceDate()));
        }

        // Publishing is what makes a route real, so this is where the orders on it stop being
        // loose work and become planned. Driven here rather than by hand, which is what it was until
        // now: the state machine has had these transitions since M1 and nothing used them.
        final var planned = consignmentIdsOf(plan);
        consignments.markPlanned(planned);

        // Anything that was on the version this replaces and is not on the new one has been pulled
        // off the route, so it goes back to the pool. That is what PLANNED -> PENDING is for.
        final var dropped = new HashSet<>(superseded);
        dropped.removeAll(planned);
        if (!dropped.isEmpty()) {
            consignments.markPending(dropped);
        }

        return PlanQueryService.toResponse(plan);
    }

    private static Set<Long> consignmentIdsOf(Plan plan) {
        final var ids = new HashSet<Long>(plan.getStops().size());
        for (final var stop : plan.getStops()) {
            ids.add(stop.getConsignmentId());
        }
        return ids;
    }
}
