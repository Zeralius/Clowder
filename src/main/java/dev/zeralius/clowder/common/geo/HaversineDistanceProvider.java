package dev.zeralius.clowder.common.geo;

/**
 * Great-circle distance on a spherical Earth, for real depots and addresses.
 *
 * <p>Travel time is derived from a single average speed. That is deliberately crude — a real
 * implementation queries a routing engine (M6's OSRM provider), and this exists so M2 and M3 can be
 * built and tested without one.
 */
public final class HaversineDistanceProvider implements DistanceProvider {

    /** IUGG mean Earth radius, metres. */
    private static final double EARTH_RADIUS_METRES = 6_371_008.8;

    private final double averageSpeedKph;

    public HaversineDistanceProvider(double averageSpeedKph) {
        if (averageSpeedKph <= 0) {
            throw new IllegalArgumentException("averageSpeedKph must be positive: " + averageSpeedKph);
        }
        this.averageSpeedKph = averageSpeedKph;
    }

    @Override
    public long distanceBetween(LatLon from, LatLon to) {
        return Math.round(metresBetween(from, to));
    }

    @Override
    public int travelTimeMinutes(LatLon from, LatLon to) {
        var metresPerMinute = averageSpeedKph * 1000d / 60d;
        return (int) Math.round(metresBetween(from, to) / metresPerMinute);
    }

    @Override
    public long minorUnitsPerUnit() {
        return 1L; // whole metres
    }

    private static double metresBetween(LatLon from, LatLon to) {
        var lat1 = Math.toRadians(from.latitude().doubleValue());
        var lat2 = Math.toRadians(to.latitude().doubleValue());
        var deltaLat = lat2 - lat1;
        var deltaLon = Math.toRadians(to.longitude().doubleValue() - from.longitude().doubleValue());

        var a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        return EARTH_RADIUS_METRES * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
