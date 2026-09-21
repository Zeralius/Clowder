package dev.zeralius.clowder.planning.feasibility;

import dev.zeralius.clowder.planning.model.ProblemInstance;
import dev.zeralius.clowder.planning.model.VehicleSpec;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes the schedule for an ordered route and reports every constraint violation.
 *
 * <p>Stateless, side-effect free, framework free. Given the same arguments it returns the same value,
 * which is what lets M3 evaluate speculative moves without defensive copying.
 *
 * <h2>Semantics</h2>
 *
 * <ul>
 *   <li><b>Service start is constrained, not service finish.</b> A stop whose service runs past its
 *       window end is feasible provided it began in time. This is standard VRPTW.
 *   <li><b>Windows are closed.</b> Starting service exactly at {@code windowEndMinutes} is feasible
 *       (decision D2).
 *   <li><b>Waiting is not a violation.</b> Arriving before the window opens is ordinary operation.
 *   <li><b>Peak load, not final load.</b> Tracked as a running maximum so that adding pickups later
 *       needs no change here; with deliveries only the peak is always the depot departure.
 * </ul>
 *
 * <h2>Violation order</h2>
 *
 * <p>Deterministic: capacity first (known before the route runs), then time-window misses in visit
 * order, then shift overrun, then depot return. M3 must produce identical output for identical
 * input, and a list whose order depended on a hash would quietly break that.
 *
 * <h2>Allocation</h2>
 *
 * <p>This allocates a schedule list and a violation list, which is right for a verifier and an
 * explainer. M3 must not call it in its inner loop: it computes distance deltas for candidate moves
 * and calls this only to verify a finished solution.
 */
public final class RouteEvaluator {

    /** {@code atStopIndex} value meaning "leaving the depot, before the first stop". */
    public static final int DEPOT_DEPARTURE = -1;

    private static final int[] EMPTY_ROUTE = new int[0];

    /**
     * @param routePositions positions in {@code instance.stops()}, in visit order; the depot is
     *     implicit at both ends
     */
    public RouteEvaluation evaluate(VehicleSpec vehicle, int[] routePositions, ProblemInstance instance) {
        final var route = routePositions == null ? EMPTY_ROUTE : routePositions;
        final var stops = instance.stops();
        final var matrix = instance.matrix();
        final var depotIndex = vehicle.depotIndex();
        final var shiftEnd = vehicle.shiftEndMinutes();
        final var n = route.length;

        // Everything is loaded at the depot, so the vehicle leaves carrying the whole demand.
        long loadGrams = 0;
        long loadMillilitres = 0;
        for (var i = 0; i < n; i++) {
            final var stop = stops.get(route[i]);
            loadGrams += stop.weightGrams();
            loadMillilitres += stop.volumeMillilitres();
        }

        var peakGrams = loadGrams;
        var peakMillilitres = loadMillilitres;
        var peakGramsAt = DEPOT_DEPARTURE;
        var peakMillilitresAt = DEPOT_DEPARTURE;

        final var schedule = new ArrayList<StopSchedule>(n);
        final var windowMisses = new ArrayList<Violation>();

        var previousIndex = depotIndex;
        var departure = vehicle.shiftStartMinutes();
        long cumulativeDistance = 0L;
        var totalWait = 0;

        // Plain loop over primitives: no streams, no Optional, no boxing. This is the hot path.
        for (var i = 0; i < n; i++) {
            final var position = route[i];
            final var stop = stops.get(position);
            final var index = stop.index();

            cumulativeDistance += matrix.distance(previousIndex, index);

            final var arrival = departure + matrix.travelTime(previousIndex, index);
            final var serviceStart = Math.max(arrival, stop.windowStartMinutes());
            final var wait = serviceStart - arrival;
            departure = serviceStart + stop.serviceMinutes();

            // Strictly greater: starting exactly at the window end is feasible (D2).
            if (serviceStart > stop.windowEndMinutes()) {
                windowMisses.add(
                        new Violation.TimeWindowMissed(stop.consignmentId(), arrival, stop.windowEndMinutes()));
            }

            loadGrams -= stop.weightGrams();
            loadMillilitres -= stop.volumeMillilitres();
            // Never true while the fleet only delivers; correct the day it also collects.
            if (loadGrams > peakGrams) {
                peakGrams = loadGrams;
                peakGramsAt = position;
            }
            if (loadMillilitres > peakMillilitres) {
                peakMillilitres = loadMillilitres;
                peakMillilitresAt = position;
            }

            totalWait += wait;
            schedule.add(new StopSchedule(
                    position, arrival, serviceStart, wait, departure, cumulativeDistance, loadGrams));
            previousIndex = index;
        }

        // The return leg. For an empty route this is depot-to-depot: zero distance, zero time.
        cumulativeDistance += matrix.distance(previousIndex, depotIndex);
        final var returnMinutes = departure + matrix.travelTime(previousIndex, depotIndex);

        final var violations = new ArrayList<Violation>(windowMisses.size() + 4);

        if (peakGrams > vehicle.capacityGrams()) {
            violations.add(new Violation.CapacityExceeded(
                    CapacityKind.WEIGHT, vehicle.capacityGrams(), peakGrams, peakGramsAt));
        }
        if (peakMillilitres > vehicle.capacityMillilitres()) {
            violations.add(new Violation.CapacityExceeded(
                    CapacityKind.VOLUME, vehicle.capacityMillilitres(), peakMillilitres, peakMillilitresAt));
        }

        violations.addAll(windowMisses);

        if (n > 0 && departure > shiftEnd) {
            violations.add(new Violation.ShiftOverrun(departure, shiftEnd));
        }
        if (returnMinutes > shiftEnd) {
            violations.add(new Violation.DepotReturnMissed(returnMinutes, shiftEnd));
        }

        return new RouteEvaluation(
                violations.isEmpty(),
                cumulativeDistance,
                returnMinutes - vehicle.shiftStartMinutes(),
                totalWait,
                peakGrams,
                schedule,
                violations);
    }
}
