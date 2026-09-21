package dev.zeralius.clowder.planning.solver;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * A complete assignment of stops to routes.
 *
 * @param unassigned stop positions no route could take — empty in a valid solution
 * @param totalDistance sum over routes, in the matrix's minor units
 */
public record Solution(List<Route> routes, int[] unassigned, long totalDistance) {

    /**
     * The Solomon objective: fewest unrouted customers, then fewest vehicles, then shortest distance.
     * The order is the whole point — reverse the last two and your numbers compare to nothing.
     */
    public static final Comparator<Solution> LEXICOGRAPHIC = Comparator.comparingInt(
                    (Solution s) -> s.unassigned().length)
            .thenComparingInt(Solution::vehicleCount)
            .thenComparingLong(Solution::totalDistance);

    public Solution {
        routes = List.copyOf(routes);
        unassigned = unassigned.clone();
    }

    @Override
    public int[] unassigned() {
        return unassigned.clone();
    }

    /** Empty routes do not count: a vehicle that never left the depot was not used. */
    public int vehicleCount() {
        return (int) routes.stream().filter(r -> !r.isEmpty()).count();
    }

    public boolean isComplete() {
        return unassigned.length == 0;
    }

    public boolean isBetterThan(Solution other) {
        return other == null || LEXICOGRAPHIC.compare(this, other) < 0;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Solution s
                && totalDistance == s.totalDistance
                && routes.equals(s.routes)
                && Arrays.equals(unassigned, s.unassigned);
    }

    @Override
    public int hashCode() {
        return routes.hashCode() * 31 + Arrays.hashCode(unassigned);
    }
}
