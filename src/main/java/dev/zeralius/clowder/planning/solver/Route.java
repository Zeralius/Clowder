package dev.zeralius.clowder.planning.solver;

import java.util.Arrays;

/**
 * One vehicle's ordered visit list. The depot is implicit at both ends.
 *
 * <p>{@code equals}, {@code hashCode} and {@code toString} are overridden because the generated ones
 * compare the array by reference: two routes visiting the same stops in the same order would be
 * unequal, and the determinism test would pass while proving nothing.
 *
 * @param stopPositions positions in {@code ProblemInstance.stops()}, in visit order
 * @param distance in the matrix's minor units (decision D7)
 */
public record Route(long vehicleId, int[] stopPositions, long distance, int durationMinutes) {

    public Route {
        stopPositions = stopPositions.clone();
    }

    /** Defensive copy: this is a result type, not a search structure, so the cost is irrelevant. */
    @Override
    public int[] stopPositions() {
        return stopPositions.clone();
    }

    public int stopCount() {
        return stopPositions.length;
    }

    public int stopAt(int visitOrder) {
        return stopPositions[visitOrder];
    }

    public boolean isEmpty() {
        return stopPositions.length == 0;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Route r
                && vehicleId == r.vehicleId
                && distance == r.distance
                && durationMinutes == r.durationMinutes
                && Arrays.equals(stopPositions, r.stopPositions);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(stopPositions) * 31 + Long.hashCode(vehicleId);
    }

    @Override
    public String toString() {
        return "Route[vehicle=%d, stops=%s, distance=%d]".formatted(vehicleId, Arrays.toString(stopPositions), distance);
    }
}
