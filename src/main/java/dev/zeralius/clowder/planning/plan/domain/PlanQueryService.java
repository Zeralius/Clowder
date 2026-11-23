package dev.zeralius.clowder.planning.plan.domain;

import dev.zeralius.clowder.common.error.NotFoundException;
import dev.zeralius.clowder.planning.PlanningFacade;
import dev.zeralius.clowder.planning.plan.api.PlanResponse;
import dev.zeralius.clowder.planning.plan.api.PlanRunResponse;
import dev.zeralius.clowder.planning.plan.api.PlanStopResponse;
import dev.zeralius.clowder.planning.plan.persistence.PlanRepository;
import dev.zeralius.clowder.planning.plan.persistence.PlanRunRepository;
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

    public PlanQueryService(PlanRunRepository runs, PlanRepository plans) {
        this.runs = runs;
        this.plans = plans;
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
        return toResponse(plan);
    }

    /** Every version for a vehicle-day, newest first, so the supersede chain is visible. */
    @Transactional(readOnly = true)
    public List<PlanResponse> findVersions(Long vehicleId, LocalDate serviceDate) {
        return plans.findWithStopsByVehicleIdAndServiceDateOrderByVersionNoDesc(vehicleId, serviceDate).stream()
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
                run.getUnassigned().stream().map(UnassignedConsignment::getReference).toList(),
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
