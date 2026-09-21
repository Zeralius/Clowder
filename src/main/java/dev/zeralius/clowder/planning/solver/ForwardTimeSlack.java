package dev.zeralius.clowder.planning.solver;

import dev.zeralius.clowder.planning.model.ProblemInstance;
import dev.zeralius.clowder.planning.model.VehicleSpec;

/**
 * Savelsbergh's forward time slack: for each position, the largest delay to its service start that
 * nothing downstream — including the depot return — would notice.
 *
 * <pre>
 * slack[i] = min( windowEnd[i] - serviceStart[i],      how much later i itself may start
 *                 wait[i+1] + slack[i+1] )              what the rest of the route can absorb
 * </pre>
 *
 * with the tail case {@code shiftEnd - returnTime}: the return leg is what actually binds, since the
 * vehicle gets back later than it finishes. A push of Δ at position i is feasible iff
 * {@code Δ <= slack[i]}. One backward pass gives the whole array; every later question is a
 * comparison.
 *
 * <p><b>A pruning aid only. Slack rejects; the full scan confirms.</b> Use it to skip candidates
 * cheaply and keep {@code RouteCalculator.isFeasible} as the gate on every move actually applied.
 * Wired that way, a bug here can only cost solution quality, which the benchmark table will show;
 * wired the other way, the same bug ships infeasible routes.
 */
final class ForwardTimeSlack {

    private ForwardTimeSlack() {}

    static int[] compute(int[] route, int length, VehicleSpec vehicle, ProblemInstance instance) {
        if (length == 0) {
            return new int[0];
        }

        final var stops = instance.stops();
        final var matrix = instance.matrix();
        final var depot = vehicle.depotIndex();

        final var serviceStart = new int[length];
        final var wait = new int[length];

        var previous = depot;
        var departure = vehicle.shiftStartMinutes();
        for (var i = 0; i < length; i++) {
            final var stop = stops.get(route[i]);
            final var index = stop.index();
            final var arrival = departure + matrix.travelTime(previous, index);
            serviceStart[i] = Math.max(arrival, stop.windowStartMinutes());
            wait[i] = serviceStart[i] - arrival;
            departure = serviceStart[i] + stop.serviceMinutes();
            previous = index;
        }

        final var returnTime = departure + matrix.travelTime(previous, depot);
        final var slack = new int[length];

        // The return leg binds harder than the last departure, because the vehicle arrives later still.
        var downstream = vehicle.shiftEndMinutes() - returnTime;

        for (var i = length - 1; i >= 0; i--) {
            final var own = stops.get(route[i]).windowEndMinutes() - serviceStart[i];
            slack[i] = Math.min(own, downstream);
            if (i > 0) {
                downstream = wait[i] + slack[i];
            }
        }

        return slack;
    }

    /**
     * How much later the stop currently at {@code at} would start service if {@code candidate} were
     * inserted just before it. Zero or negative means no later at all. Compared against
     * {@code slack[at]} it is the cheap rejection: four lookups instead of an array copy and a scan.
     *
     * @param serviceStarts the current route's service starts, from {@code RouteCalculator.serviceStarts}
     */
    static int pushOnNext(
            int[] route,
            int length,
            int at,
            int candidate,
            int[] serviceStarts,
            VehicleSpec vehicle,
            ProblemInstance instance) {
        final var stops = instance.stops();
        final var matrix = instance.matrix();

        final int before;
        final int departureFromBefore;
        if (at == 0) {
            before = vehicle.depotIndex();
            departureFromBefore = vehicle.shiftStartMinutes();
        } else {
            final var previousStop = stops.get(route[at - 1]);
            before = previousStop.index();
            departureFromBefore = serviceStarts[at - 1] + previousStop.serviceMinutes();
        }

        final var inserted = stops.get(candidate);
        final var arrivalAtInserted = departureFromBefore + matrix.travelTime(before, inserted.index());
        final var departureFromInserted =
                Math.max(arrivalAtInserted, inserted.windowStartMinutes()) + inserted.serviceMinutes();

        final var next = stops.get(route[at]);
        final var newArrivalAtNext = departureFromInserted + matrix.travelTime(inserted.index(), next.index());

        return newArrivalAtNext - serviceStarts[at];
    }
}
