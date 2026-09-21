package dev.zeralius.clowder.planning.feasibility;

import java.util.List;

/**
 * The complete verdict on one route.
 *
 * @param totalDistance in the matrix's minor units — divide by {@code
 *     DistanceMatrix.minorUnitsPerUnit()} for whole units (decision D7). Not metres: Solomon
 *     coordinates are plane units
 * @param totalDurationMinutes depot departure to depot return, including waiting
 * @param peakLoadGrams the running maximum, which for deliveries-only is the load leaving the depot
 * @param violations every violation found, never just the first; empty exactly when feasible
 */
public record RouteEvaluation(
        boolean feasible,
        long totalDistance,
        int totalDurationMinutes,
        int totalWaitMinutes,
        long peakLoadGrams,
        List<StopSchedule> schedule,
        List<Violation> violations) {

    public RouteEvaluation {
        schedule = List.copyOf(schedule);
        violations = List.copyOf(violations);
    }

    /** Convenience for tests and for M3's assertion that a returned solution really is feasible. */
    public <T extends Violation> List<T> violationsOfType(Class<T> type) {
        return violations.stream().filter(type::isInstance).map(type::cast).toList();
    }
}
