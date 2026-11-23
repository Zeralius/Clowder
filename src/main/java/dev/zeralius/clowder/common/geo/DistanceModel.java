package dev.zeralius.clowder.common.geo;

/**
 * Which geometry a set of coordinates is in, and therefore how to measure between them.
 *
 * <p>This is a property of the data, not a setting. Solomon instance coordinates are abstract plane
 * units that happen to fit inside a latitude and longitude range; projecting them onto a sphere would
 * silently destroy any comparison with published results. A real depot is degrees on a sphere, and
 * measuring that with Pythagoras is just as wrong, only less obviously so.
 *
 * <p>The model therefore travels with the depot whose coordinates they are, and nothing has to guess.
 */
public enum DistanceModel {

    /**
     * Plane units, measured with Pythagoras, where travel time equals distance because the Solomon
     * instances define speed as 1. The speed argument is ignored for exactly that reason.
     */
    EUCLIDEAN {
        @Override
        public DistanceProvider provider(double averageSpeedKph) {
            return new EuclideanDistanceProvider();
        }
    },

    /** Degrees on a sphere, measured in whole metres, with travel time from an average speed. */
    HAVERSINE {
        @Override
        public DistanceProvider provider(double averageSpeedKph) {
            return new HaversineDistanceProvider(averageSpeedKph);
        }
    };

    /**
     * A provider for this model.
     *
     * @param averageSpeedKph used to turn distance into time. One matrix serves every vehicle in a
     *     run, so this cannot be per-vehicle; callers pass the slowest vehicle at the depot, because
     *     a matrix that is optimistic about travel time produces plans nobody can drive.
     */
    public abstract DistanceProvider provider(double averageSpeedKph);
}
