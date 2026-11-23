package dev.zeralius.clowder.execution.domain;

import dev.zeralius.clowder.common.geo.EuclideanDistanceProvider;
import dev.zeralius.clowder.common.geo.LatLon;
import dev.zeralius.clowder.consignment.ConsignmentFacade;
import dev.zeralius.clowder.consignment.api.ConsignmentResponse;
import dev.zeralius.clowder.execution.api.ProjectedMiss;
import dev.zeralius.clowder.execution.api.TrackingResponse;
import dev.zeralius.clowder.execution.persistence.DriverEventRepository;
import dev.zeralius.clowder.fleet.FleetFacade;
import dev.zeralius.clowder.planning.PlanningFacade;
import dev.zeralius.clowder.planning.feasibility.RouteEvaluator;
import dev.zeralius.clowder.planning.feasibility.Violation;
import dev.zeralius.clowder.planning.model.DistanceMatrix;
import dev.zeralius.clowder.planning.model.ProblemInstance;
import dev.zeralius.clowder.planning.model.StopSpec;
import dev.zeralius.clowder.planning.model.VehicleSpec;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plan against reality, with the remaining route re-evaluated from where the vehicle actually is.
 *
 * <p><b>This is the payoff for M2.</b> Projected window misses are not a new algorithm — they are the
 * feasibility engine, run over the stops that are left with a new start time. Having built it as a
 * pure function with no framework attached is why this method is thirty lines rather than a module.
 *
 * <p><b>Where the vehicle is.</b> Not from GPS: the last completed stop, at its reported departure,
 * or the depot if nothing is done yet. Device coordinates are advisory, and deciding state from them
 * is how a car park turns into a delivery.
 */
@Service
public class DivergenceService {

    private final PlanningFacade plans;
    private final DriverEventRepository events;
    private final ConsignmentFacade consignments;
    private final FleetFacade fleet;
    private final PlanProjector projector = new PlanProjector();
    private final RouteEvaluator evaluator = new RouteEvaluator();

    public DivergenceService(
            PlanningFacade plans,
            DriverEventRepository events,
            ConsignmentFacade consignments,
            FleetFacade fleet) {
        this.plans = plans;
        this.events = events;
        this.consignments = consignments;
        this.fleet = fleet;
    }

    @Transactional(readOnly = true)
    public TrackingResponse track(Long planId, Instant now) {
        final var plan = plans.findPlan(planId);

        final var projection =
                projector.project(planId, plan.stops(), events.findByPlanIdOrderByOccurredAtAscIdAsc(planId));

        final var misses = projectMisses(plan.depotId(), plan.vehicleId(), plan.serviceDate(), projection, now);

        return new TrackingResponse(
                planId,
                projection.completed(),
                projection.remaining(),
                projection.currentDelayMinutes(),
                projection.lastKnownDeparture(),
                projection.stops(),
                misses);
    }

    /**
     * Builds a one-vehicle problem holding only the stops that are left, starting from where the
     * vehicle is and when it is actually running, and asks the evaluator. Its {@code TimeWindowMissed}
     * violations are the projected misses.
     *
     * <p>The windows come from the consignments as they stand now rather than from the plan snapshot,
     * because a dispatcher watching the day cares about the promise that is currently live. The
     * snapshot on {@code plan_stop} answers a different question — what was promised at publish — and
     * that is what {@code currentDelayMinutes} is measured against.
     */
    private List<ProjectedMiss> projectMisses(
            Long depotId, Long vehicleId, LocalDate serviceDate, PlanProjection projection, Instant now) {

        final var remaining = projection.remainingStops();
        if (remaining.isEmpty()) {
            return List.of();
        }

        final var byId = byConsignmentId(consignments.findForServiceDate(depotId, serviceDate));

        final var origin = originOf(projection, byId, depotId);

        // The vehicle has been driving since its last report, so the clock is whichever is later.
        final var startedAt = projection.lastKnownDeparture() == null
                ? now
                : latestOf(projection.lastKnownDeparture(), now);

        final var points = new ArrayList<LatLon>();
        points.add(origin);

        final var stops = new ArrayList<StopSpec>();
        final var windowEnds = new HashMap<Long, Instant>();
        final var references = new HashMap<Long, String>();

        for (final var state : remaining) {
            final var consignment = byId.get(state.consignmentId());
            if (consignment == null) {
                // Cancelled, or moved to another depot-day. Nothing to project.
                continue;
            }
            points.add(consignment.location());
            stops.add(new StopSpec(
                    consignment.id(),
                    points.size() - 1,
                    consignment.weightGrams(),
                    consignment.volumeMillilitres(),
                    consignment.serviceMinutes(),
                    minutesBetween(startedAt, consignment.window().start()),
                    minutesBetween(startedAt, consignment.window().end())));
            windowEnds.put(consignment.id(), consignment.window().end());
            references.put(consignment.id(), consignment.reference());
        }

        if (stops.isEmpty()) {
            return List.of();
        }

        // Euclidean, the same hard-coded choice the planner makes, so the projection and the plan
        // disagree about geography in the same direction rather than in two different ones.
        final var matrix = DistanceMatrix.from(points, new EuclideanDistanceProvider());

        // A deliberately unconstrained vehicle: the question is about time windows, not capacity or
        // shift length, and a capacity violation here would say nothing a dispatcher could act on.
        final var vehicle = new VehicleSpec(vehicleId, Long.MAX_VALUE, Long.MAX_VALUE, 0, Integer.MAX_VALUE, 0);
        final var instance = new ProblemInstance(List.of(vehicle), stops, matrix, 0);

        final var order = new int[stops.size()];
        for (var i = 0; i < order.length; i++) {
            order[i] = i;
        }

        final var evaluation = evaluator.evaluate(vehicle, order, instance);

        final var misses = new ArrayList<ProjectedMiss>();
        for (final var violation : evaluation.violationsOfType(Violation.TimeWindowMissed.class)) {
            misses.add(new ProjectedMiss(
                    violation.consignmentId(),
                    references.get(violation.consignmentId()),
                    startedAt.plusSeconds(violation.arrivalMinutes() * 60L),
                    windowEnds.get(violation.consignmentId()),
                    violation.arrivalMinutes() - violation.windowEndMinutes()));
        }
        return misses;
    }

    private LatLon originOf(PlanProjection projection, Map<Long, ConsignmentResponse> byId, Long depotId) {
        if (projection.lastCompletedStopId() != null) {
            final var last = projection.stops().stream()
                    .filter(stop -> projection.lastCompletedStopId().equals(stop.planStopId()))
                    .findFirst()
                    .map(stop -> byId.get(stop.consignmentId()));
            if (last.isPresent()) {
                return last.get().location();
            }
        }

        final var depot = fleet.findDepotById(depotId);
        return new LatLon(depot.latitude(), depot.longitude());
    }

    private static Map<Long, ConsignmentResponse> byConsignmentId(List<ConsignmentResponse> all) {
        final var byId = new HashMap<Long, ConsignmentResponse>(all.size());
        for (final var consignment : all) {
            byId.put(consignment.id(), consignment);
        }
        return byId;
    }

    private static Instant latestOf(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static int minutesBetween(Instant from, Instant to) {
        return Math.toIntExact(Duration.between(from, to).toMinutes());
    }
}
