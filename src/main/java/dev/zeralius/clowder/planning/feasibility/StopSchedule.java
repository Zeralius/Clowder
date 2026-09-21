package dev.zeralius.clowder.planning.feasibility;

/**
 * What happened at one stop. The list position within {@link RouteEvaluation#schedule()} is the visit
 * order; {@code stopIndex} points back into the instance.
 *
 * @param stopIndex position in {@code ProblemInstance.stops()}
 * @param waitMinutes time spent waiting for the window to open — normal, never a violation
 * @param cumulativeDistance distance travelled from the depot up to and including arriving here
 * @param loadAfter what remains on the vehicle once this stop has been served
 */
public record StopSchedule(
        int stopIndex,
        int arrivalMinutes,
        int serviceStartMinutes,
        int waitMinutes,
        int departureMinutes,
        long cumulativeDistance,
        long loadAfter) {}
