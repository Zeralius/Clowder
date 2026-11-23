package dev.zeralius.clowder.planning.plan.domain;

import dev.zeralius.clowder.consignment.api.ConsignmentResponse;
import dev.zeralius.clowder.planning.feasibility.RouteEvaluator;
import dev.zeralius.clowder.planning.model.ProblemInstance;
import dev.zeralius.clowder.planning.model.VehicleSpec;
import dev.zeralius.clowder.planning.solver.Route;
import dev.zeralius.clowder.planning.solver.Solution;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Converts a solver {@link Solution} into draft {@link Plan} entities with frozen times.
 *
 * <p><b>Why it reuses the evaluator.</b> The solver returns visit orders and totals, not per-stop
 * times. Rather than recompute the schedule with a third copy of the arithmetic, the assembler asks
 * M2 — the same code that already verified the solution — and copies the answer into rows. Three
 * implementations of one schedule would be three chances to disagree.
 *
 * <p>Not a Spring bean: it has no dependency it cannot be handed, so its test needs no context.
 */
public final class PlanAssembler {

    private final RouteEvaluator evaluator = new RouteEvaluator();

    /**
     * @param epoch the service date at midnight in the depot zone; solver minutes are offsets from here
     * @param serviceDate passed explicitly rather than read back out of {@code epoch}, which would only
     *     be correct by coincidence
     * @param consignments in stop-position order, for the reference and address snapshot
     * @param nextVersionFor supplies the version number for a vehicle, normally {@code maxVersionNo + 1}
     */
    public List<Plan> assemble(
            Long planRunId,
            Long depotId,
            Solution solution,
            ProblemInstance instance,
            Instant epoch,
            LocalDate serviceDate,
            List<ConsignmentResponse> consignments,
            ToIntFunction<Long> nextVersionFor) {

        final var plans = new ArrayList<Plan>(solution.routes().size());

        for (final var route : solution.routes()) {
            if (route.isEmpty()) {
                continue;
            }
            plans.add(
                    assembleOne(planRunId, depotId, route, instance, epoch, serviceDate, consignments, nextVersionFor));
        }

        return plans;
    }

    private Plan assembleOne(
            Long planRunId,
            Long depotId,
            Route route,
            ProblemInstance instance,
            Instant epoch,
            LocalDate serviceDate,
            List<ConsignmentResponse> consignments,
            ToIntFunction<Long> nextVersionFor) {

        final var vehicle = vehicleOf(instance, route.vehicleId());
        final var evaluation = evaluator.evaluate(vehicle, route.stopPositions(), instance);

        final var plan = new Plan(
                planRunId,
                depotId,
                route.vehicleId(),
                serviceDate,
                nextVersionFor.applyAsInt(route.vehicleId()),
                evaluation.totalDistance(),
                evaluation.totalDurationMinutes());

        var sequence = 0;
        for (final var visit : evaluation.schedule()) {
            final var consignment = consignments.get(visit.stopIndex());
            plan.addStop(new PlanStop(
                    sequence++,
                    consignment.id(),
                    consignment.reference(),
                    consignment.addressLine(),
                    at(epoch, visit.arrivalMinutes()),
                    at(epoch, visit.serviceStartMinutes()),
                    at(epoch, visit.departureMinutes()),
                    visit.waitMinutes(),
                    visit.cumulativeDistance(),
                    visit.loadAfter()));
        }

        return plan;
    }

    private static VehicleSpec vehicleOf(ProblemInstance instance, long vehicleId) {
        for (final var vehicle : instance.vehicles()) {
            if (vehicle.id() == vehicleId) {
                return vehicle;
            }
        }
        throw new IllegalStateException("Route references unknown vehicle " + vehicleId);
    }

    /** Minutes from the epoch back to an absolute instant. Crossing midnight is not a special case. */
    private static Instant at(Instant epoch, int minutes) {
        return epoch.plusSeconds(minutes * 60L);
    }
}
