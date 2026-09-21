package dev.zeralius.clowder.planning.model;

import dev.zeralius.clowder.common.geo.DistanceProvider;
import dev.zeralius.clowder.common.geo.LatLon;
import java.util.List;

/**
 * Precomputed pairwise distances and travel times, indexed by position in the point list the matrix
 * was built from. Immutable once built; safe to share across threads.
 *
 * <p>Two matrices, not one (D7/D8): distance accumulates for the objective, travel time accumulates
 * for the schedule, and in Solomon they are numerically equal but semantically not.
 *
 * <p>No bounds checking on the accessors — the array does it, and this is the hottest code in the
 * project. An {@code IndexOutOfBoundsException} here means the caller built the wrong instance.
 */
public final class DistanceMatrix {

    private final long[][] distance;
    private final int[][] travelTime;
    private final long minorUnitsPerUnit;

    private DistanceMatrix(long[][] distance, int[][] travelTime, long minorUnitsPerUnit) {
        this.distance = distance;
        this.travelTime = travelTime;
        this.minorUnitsPerUnit = minorUnitsPerUnit;
    }

    /**
     * Builds the full matrix. Symmetric providers compute one triangle and mirror it; asymmetric ones
     * (a road network, where one-way streets make A to B differ from B to A) compute every cell.
     */
    public static DistanceMatrix from(List<LatLon> points, DistanceProvider provider) {
        var n = points.size();
        var distance = new long[n][n];
        var travelTime = new int[n][n];
        var symmetric = provider.isSymmetric();

        for (var i = 0; i < n; i++) {
            var from = points.get(i);
            var start = symmetric ? i + 1 : 0;
            for (var j = start; j < n; j++) {
                if (i == j) {
                    continue; // diagonal stays zero
                }
                var to = points.get(j);
                var d = provider.distanceBetween(from, to);
                var t = provider.travelTimeMinutes(from, to);
                distance[i][j] = d;
                travelTime[i][j] = t;
                if (symmetric) {
                    distance[j][i] = d;
                    travelTime[j][i] = t;
                }
            }
        }

        return new DistanceMatrix(distance, travelTime, provider.minorUnitsPerUnit());
    }

    public long distance(int from, int to) {
        return distance[from][to];
    }

    public int travelTime(int from, int to) {
        return travelTime[from][to];
    }

    public int size() {
        return distance.length;
    }

    /** For rendering and the benchmark table: divide a total by this to get whole base units. */
    public long minorUnitsPerUnit() {
        return minorUnitsPerUnit;
    }
}
