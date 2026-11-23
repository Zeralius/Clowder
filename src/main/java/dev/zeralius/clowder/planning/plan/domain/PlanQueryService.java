package dev.zeralius.clowder.planning.plan.domain;

import dev.zeralius.clowder.common.error.ForbiddenException;
import dev.zeralius.clowder.common.error.NotFoundException;
import dev.zeralius.clowder.planning.PlanningFacade;
import dev.zeralius.clowder.planning.plan.api.PlanResponse;
import dev.zeralius.clowder.planning.plan.api.PlanRunResponse;
import dev.zeralius.clowder.planning.plan.api.PlanStopResponse;
import dev.zeralius.clowder.planning.plan.persistence.PlanRepository;
import dev.zeralius.clowder.planning.plan.persistence.PlanRunRepository;
import dev.zeralius.clowder.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read side. Mapping happens inside the transaction, because the stop list and the unassigned
 * list are both lazy and {@code open-in-view} is off.
 */
@Service
public class PlanQueryService implements PlanningFacade {

    private final PlanRunRepository runs;
    private final PlanRepository plans;
    private final CurrentUser currentUser;

    public PlanQueryService(PlanRunRepository runs, PlanRepository plans, CurrentUser currentUser) {
        this.runs = runs;
        this.plans = plans;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public PlanRunResponse findRun(Long id) {
        final var run = runs.findWithUnassignedById(id).orElseThrow(() -> new NotFoundException("Plan run", id));
        return toResponse(run);
    }

    /** Execution reads a plan by id through this, so it never touches a planning entity. */
    @Override
    @Transactional(readOnly = true)
    public PlanResponse findPlan(Long id) {
        final var plan = plans.findWithStopsById(id).orElseThrow(() -> new NotFoundException("Plan", id));
        requireAccess(plan.getDriverId());
        return toResponse(plan);
    }

    /**
     * A driver may read the plan they were given, and not anybody else. Dispatchers and admins read
     * any of them. The rule itself lives on {@code AuthenticatedUser} so that this and the event
     * ingestion path cannot disagree about what it is.
     */
    private void requireAccess(Long planDriverId) {
        if (!currentUser.require().mayActOnPlanAssignedTo(planDriverId)) {
            throw new ForbiddenException("This plan is not assigned to you");
        }
    }

    /** Rostering a driver onto a plan. Dispatcher work, enforced in the filter chain. */
    @Transactional
    public PlanResponse assignDriver(Long planId, Long driverId) {
        final var plan = plans.findWithStopsById(planId).orElseThrow(() -> new NotFoundException("Plan", planId));
        plan.assignTo(driverId);
        return toResponse(plan);
    }

    /**
     * Every version for a vehicle-day, newest first, so the supersede chain is visible.
     *
     * <p>Filtered rather than refused for a driver: a vehicle-day may hold versions that were someone
     * else's before a reassignment, and answering 403 for the whole list because of one of them would
     * be useless. A driver sees their own versions; an empty list is a truthful answer.
     */
    @Transactional(readOnly = true)
    public List<PlanResponse> findVersions(Long vehicleId, LocalDate serviceDate) {
        final var user = currentUser.require();
        return plans.findWithStopsByVehicleIdAndServiceDateOrderByVersionNoDesc(vehicleId, serviceDate).stream()
                .filter(plan -> user.mayActOnPlanAssignedTo(plan.getDriverId()))
                .map(PlanQueryService::toResponse)
                .toList();
    }

    /** The one door execution comes through. */
    @Override
    @Transactional(readOnly = true)
    public Optional<PlanResponse> findPublishedPlan(Long vehicleId, LocalDate serviceDate) {
        return plans.findWithStopsByVehicleIdAndServiceDateAndStatus(vehicleId, serviceDate, PlanStatus.PUBLISHED)
                .map(PlanQueryService::toResponse);
    }

    /**
     * For callers that already hold a managed plan, such as the publisher. It must be called inside
     * the transaction that loaded it: the stop list is lazy and {@code open-in-view} is off.
     */
    public static PlanResponse toResponse(Plan plan) {
        return new PlanResponse(
                plan.getId(),
                plan.getPlanRunId(),
                plan.getDepotId(),
                plan.getVehicleId(),
                plan.getDriverId(),
                plan.getServiceDate(),
                plan.getVersionNo(),
                plan.getStatus(),
                plan.getTotalDistance(),
                plan.getTotalDurationMinutes(),
                plan.getPublishedAt(),
                plan.getCreatedAt(),
                plan.getStops().stream().map(PlanQueryService::toResponse).toList());
    }

    private PlanRunResponse toResponse(PlanRun run) {
        final var planIds = plans.findByPlanRunIdOrderByVehicleIdAsc(run.getId()).stream()
                .map(Plan::getId)
                .toList();

        return new PlanRunResponse(
                run.getId(),
                run.getDepotId(),
                run.getServiceDate(),
                run.getStatus(),
                run.getSolverVersion(),
                run.getSeed(),
                run.getBudgetMs(),
                run.getElapsedMs(),
                run.getIterations(),
                run.getFailureReason(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getUnassigned().stream()
                        .map(UnassignedConsignment::getReference)
                        .toList(),
                planIds);
    }

    private static PlanStopResponse toResponse(PlanStop stop) {
        return new PlanStopResponse(
                stop.getId(),
                stop.getSequenceNo(),
                stop.getConsignmentId(),
                stop.getReference(),
                stop.getAddressLine(),
                stop.getPlannedArrival(),
                stop.getPlannedServiceStart(),
                stop.getPlannedDeparture(),
                stop.getWaitMinutes(),
                stop.getCumulativeDistance(),
                stop.getLoadAfter());
    }
}
