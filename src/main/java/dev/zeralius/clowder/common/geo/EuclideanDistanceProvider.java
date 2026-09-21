package dev.zeralius.clowder.common.geo;

/**
 * Straight-line distance on a flat plane, for Solomon benchmark instances whose coordinates are
 * abstract plane units rather than degrees.
 *
 * <p>Travel time equals distance: Solomon instances have speed 1.
 *
 * <p>{@code doubleValue()} is fine here. The standing rule bans {@code double} where values
 * <em>accumulate</em>; a single edge is one square root, immediately rounded to a {@code long}, and
 * everything downstream of that rounding is integral.
 */
public final class EuclideanDistanceProvider implements DistanceProvider {

    /** Millionths of a plane unit — enough precision that a 100-edge route matches published totals. */
    private static final long MINOR_UNITS_PER_UNIT = 1_000_000L;

    @Override
    public long distanceBetween(LatLon from, LatLon to) {
        return Math.round(planeDistance(from, to) * MINOR_UNITS_PER_UNIT);
    }

    @Override
    public int travelTimeMinutes(LatLon from, LatLon to) {
        // Speed is 1 in Solomon instances, so time and distance are the same number.
        return (int) Math.round(planeDistance(from, to));
    }

    @Override
    public long minorUnitsPerUnit() {
        return MINOR_UNITS_PER_UNIT;
    }

    /**
     * One square root per edge, rounded immediately. The matrix is built once per planning run, so
     * this is not a hot path — the hot path reads the matrix. x maps to longitude, as in the importer.
     */
    private static double planeDistance(LatLon from, LatLon to) {
        var dx = to.longitude().doubleValue() - from.longitude().doubleValue();
        var dy = to.latitude().doubleValue() - from.latitude().doubleValue();
        return Math.hypot(dx, dy);
    }
}
