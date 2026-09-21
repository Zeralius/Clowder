package dev.zeralius.clowder.planning.solver;

import dev.zeralius.clowder.planning.model.DistanceMatrix;
import dev.zeralius.clowder.planning.model.ProblemInstance;
import dev.zeralius.clowder.planning.model.StopSpec;
import dev.zeralius.clowder.planning.model.VehicleSpec;
import java.util.List;

/**
 * Allocation-free route arithmetic for the search inner loop.
 *
 * <p>Deliberately duplicates the semantics of {@code RouteEvaluator} in a cheaper form: yes/no with
 * an early exit, instead of a schedule list and every violation. {@code RouteCalculatorAgreementTest}
 * asserts the two never disagree; if you change one, change both.
 */
final class RouteCalculator {

    private RouteCalculator() {}

    /** Total travelled distance including both depot legs, in minor units. */
    static long distance(int[] route, int length, VehicleSpec vehicle, ProblemInstance instance) {
        if (length == 0) {
            return 0L;
        }
        final var stops = instance.stops();
        final var matrix = instance.matrix();
        final var depot = vehicle.depotIndex();

        var total = 0L;
        var previous = depot;
        for (var i = 0; i < length; i++) {
            final var index = stops.get(route[i]).index();
            total += matrix.distance(previous, index);
            previous = index;
        }
        return total + matrix.distance(previous, depot);
    }

    /** Depot departure to depot return, in minutes. */
    static int duration(int[] route, int length, VehicleSpec vehicle, ProblemInstance instance) {
        return arrivalBackAtDepot(route, length, vehicle, instance) - vehicle.shiftStartMinutes();
    }

    static long load(int[] route, int length, ProblemInstance instance) {
        final var stops = instance.stops();
        var total = 0L;
        for (var i = 0; i < length; i++) {
            total += stops.get(route[i]).weightGrams();
        }
        return total;
    }

    /**
     * Whether the route satisfies capacity, every time window, and the shift. Single forward pass with
     * an early exit; no allocation.
     */
    static boolean isFeasible(int[] route, int length, VehicleSpec vehicle, ProblemInstance instance) {
        final var stops = instance.stops();
        final var matrix = instance.matrix();
        final var depot = vehicle.depotIndex();

        var weight = 0L;
        var volume = 0L;
        for (var i = 0; i < length; i++) {
            final var stop = stops.get(route[i]);
            weight += stop.weightGrams();
            volume += stop.volumeMillilitres();
        }
        if (weight > vehicle.capacityGrams() || volume > vehicle.capacityMillilitres()) {
            return false;
        }

        final var shiftEnd = vehicle.shiftEndMinutes();
        var previous = depot;
        var departure = vehicle.shiftStartMinutes();

        for (var i = 0; i < length; i++) {
            final var stop = stops.get(route[i]);
            final var index = stop.index();
            final var arrival = departure + matrix.travelTime(previous, index);
            final var serviceStart = Math.max(arrival, stop.windowStartMinutes());

            // Closed window (D2): equality is feasible.
            if (serviceStart > stop.windowEndMinutes()) {
                return false;
            }

            departure = serviceStart + stop.serviceMinutes();
            previous = index;
        }

        if (length > 0 && departure > shiftEnd) {
            return false;
        }
        return departure + matrix.travelTime(previous, depot) <= shiftEnd;
    }

    static int arrivalBackAtDepot(int[] route, int length, VehicleSpec vehicle, ProblemInstance instance) {
        final var stops = instance.stops();
        final var matrix = instance.matrix();
        final var depot = vehicle.depotIndex();

        var previous = depot;
        var departure = vehicle.shiftStartMinutes();
        for (var i = 0; i < length; i++) {
            final var stop = stops.get(route[i]);
            final var index = stop.index();
            final var arrival = departure + matrix.travelTime(previous, index);
            departure = Math.max(arrival, stop.windowStartMinutes()) + stop.serviceMinutes();
            previous = index;
        }
        return departure + matrix.travelTime(previous, depot);
    }

    /**
     * Service start at each position, written into {@code out}. Used by the I1 cost function, which
     * needs to know how much a candidate insertion delays the stop after it.
     */
    static void serviceStarts(
            int[] route, int length, VehicleSpec vehicle, ProblemInstance instance, int[] out) {
        final var stops = instance.stops();
        final var matrix = instance.matrix();

        var previous = vehicle.depotIndex();
        var departure = vehicle.shiftStartMinutes();
        for (var i = 0; i < length; i++) {
            final var stop = stops.get(route[i]);
            final var index = stop.index();
            final var arrival = departure + matrix.travelTime(previous, index);
            final var serviceStart = Math.max(arrival, stop.windowStartMinutes());
            out[i] = serviceStart;
            departure = serviceStart + stop.serviceMinutes();
            previous = index;
        }
    }

    /**
     * Distance delta of inserting {@code candidate} between positions {@code at-1} and {@code at}.
     * Four matrix lookups, no scan — this is the cheap half of delta evaluation.
     */
    static long insertionDistanceDelta(
            int[] route, int length, int at, int candidate, VehicleSpec vehicle, ProblemInstance instance) {
        final List<StopSpec> stops = instance.stops();
        final DistanceMatrix matrix = instance.matrix();
        final var depot = vehicle.depotIndex();

        final var before = at == 0 ? depot : stops.get(route[at - 1]).index();
        final var after = at == length ? depot : stops.get(route[at]).index();
        final var inserted = stops.get(candidate).index();

        return matrix.distance(before, inserted) + matrix.distance(inserted, after) - matrix.distance(before, after);
    }

    /** Copy of {@code route} with {@code candidate} spliced in at {@code at}. */
    static int[] withInsertion(int[] route, int length, int at, int candidate) {
        final var out = new int[length + 1];
        System.arraycopy(route, 0, out, 0, at);
        out[at] = candidate;
        System.arraycopy(route, at, out, at + 1, length - at);
        return out;
    }

    /** Copy of {@code route} with the entry at {@code at} removed. */
    static int[] withRemoval(int[] route, int length, int at) {
        final var out = new int[length - 1];
        System.arraycopy(route, 0, out, 0, at);
        System.arraycopy(route, at + 1, out, at, length - at - 1);
        return out;
    }
}
