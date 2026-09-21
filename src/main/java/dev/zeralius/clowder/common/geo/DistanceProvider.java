package dev.zeralius.clowder.common.geo;

/**
 * Distance and travel time between two points.
 *
 * <p>Distance and time are deliberately separate methods (decisions D7/D8). In Solomon instances speed
 * is 1, so travel time equals distance; on real geography it does not. Deriving one from the other
 * would force one relationship on both and make benchmark results incomparable.
 *
 * <p>Implementations must be stateless and thread-safe: M3 builds matrices in parallel.
 */
public interface DistanceProvider {

    /**
     * Distance in minor units — see {@link #minorUnitsPerUnit()}. Integral so that route totals
     * accumulate exactly.
     */
    long distanceBetween(LatLon from, LatLon to);

    /** Travel time in whole minutes (decision D8: a documented rounding). */
    int travelTimeMinutes(LatLon from, LatLon to);

    /**
     * How many minor units make one whole unit of this provider's base measure. Euclidean returns
     * 1_000_000 (millionths of a plane unit); haversine returns 1 (whole metres).
     */
    long minorUnitsPerUnit();

    /**
     * Whether {@code f(a, b) == f(b, a)} for both methods. True for straight-line providers; a road
     * network provider returns false and the matrix builder stops halving its work.
     */
    default boolean isSymmetric() {
        return true;
    }
}
