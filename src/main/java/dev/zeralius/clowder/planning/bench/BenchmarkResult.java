package dev.zeralius.clowder.planning.bench;

/**
 * One row of the README table. Distances are whole units, converted from the solver's minor units.
 *
 * @param gapPercent positive means worse than best-known; negative would mean better. {@code NaN}
 *     when no reference value is known for the instance
 */
public record BenchmarkResult(
        String instance,
        int vehicles,
        int bestVehicles,
        double distance,
        double bestDistance,
        double gapPercent,
        int unassigned,
        long millis) {

    public static BenchmarkResult of(
            String instance,
            int vehicles,
            double distance,
            int unassigned,
            int bestVehicles,
            double bestDistance,
            long millis) {
        final var gap = bestDistance <= 0 || Double.isNaN(bestDistance)
                ? Double.NaN
                : (distance - bestDistance) / bestDistance * 100d;
        return new BenchmarkResult(instance, vehicles, bestVehicles, distance, bestDistance, gap, unassigned, millis);
    }

    /** Extra vehicles over best-known. The first term of the objective, so it ranks above the gap. */
    public int excessVehicles() {
        return bestVehicles <= 0 ? 0 : vehicles - bestVehicles;
    }
}
