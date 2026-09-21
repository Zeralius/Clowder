package dev.zeralius.clowder.planning.feasibility;

/**
 * Everything that can be wrong with a route or a solution.
 *
 * <p>Sealed so that rendering code is checked for exhaustiveness: add a variant and every {@code
 * switch} over this type stops compiling until it handles the new case. An unsealed interface or an
 * enum-plus-message would let a violation ship that nobody displays.
 */
public sealed interface Violation {

    /**
     * Load exceeded a capacity dimension.
     *
     * @param atStopIndex position in the instance's stop list where the peak occurred, or {@link
     *     RouteEvaluator#DEPOT_DEPARTURE} when the vehicle was already overloaded leaving the depot —
     *     which is the only possibility while the fleet does deliveries and no pickups
     */
    record CapacityExceeded(CapacityKind kind, long limit, long required, int atStopIndex)
            implements Violation {}

    /**
     * The vehicle could not begin service before the window closed.
     *
     * <p>Reports arrival rather than service start: {@code serviceStart = max(arrival, windowStart)}
     * and {@code windowStart <= windowEnd}, so the two are late under exactly the same condition, and
     * arrival is the number a dispatcher can act on.
     *
     * @param arrivalMinutes when the vehicle actually arrived
     */
    record TimeWindowMissed(long consignmentId, int arrivalMinutes, int windowEndMinutes)
            implements Violation {}

    /** The last service finished after the shift ended — the driver was still working. */
    record ShiftOverrun(int routeEndMinutes, int shiftEndMinutes) implements Violation {}

    /** The vehicle got back to the depot after the shift ended. Can co-occur with {@link ShiftOverrun}. */
    record DepotReturnMissed(int returnMinutes, int shiftEndMinutes) implements Violation {}

    /**
     * Solution-level: this consignment is on no route. A route evaluator cannot know that; M3's
     * solution evaluator emits it. Declared here so the whole vocabulary lives in one place.
     */
    record UnassignedConsignment(long consignmentId) implements Violation {}
}
