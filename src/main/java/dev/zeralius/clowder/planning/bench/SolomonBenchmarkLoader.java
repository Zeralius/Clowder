package dev.zeralius.clowder.planning.bench;

import dev.zeralius.clowder.common.geo.EuclideanDistanceProvider;
import dev.zeralius.clowder.common.geo.LatLon;
import dev.zeralius.clowder.importer.SolomonInstance;
import dev.zeralius.clowder.importer.SolomonInstanceParser;
import dev.zeralius.clowder.planning.model.DistanceMatrix;
import dev.zeralius.clowder.planning.model.ProblemInstance;
import dev.zeralius.clowder.planning.model.StopSpec;
import dev.zeralius.clowder.planning.model.VehicleSpec;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a parsed Solomon instance straight into a {@link ProblemInstance}, without a database.
 *
 * <p>Benchmarking through the import API would measure Postgres and Hibernate, not the solver. The
 * conversion is deliberately the same arithmetic the importer and the planning loader use: minutes
 * from the start of the horizon, x as longitude and y as latitude, Euclidean distance.
 */
public final class SolomonBenchmarkLoader {

    private final SolomonInstanceParser parser = new SolomonInstanceParser();

    public Loaded load(Reader reader) {
        return convert(parser.parse(reader));
    }

    public Loaded convert(SolomonInstance instance) {
        final var points = new ArrayList<LatLon>(instance.customers().size() + 1);
        points.add(pointOf(instance.depot().x(), instance.depot().y()));

        final var stops = new ArrayList<StopSpec>(instance.customers().size());
        var index = 1;
        for (var customer : instance.customers()) {
            points.add(pointOf(customer.x(), customer.y()));
            stops.add(new StopSpec(
                    customer.id(),
                    index++,
                    customer.demand(),
                    0L,
                    customer.serviceTime(),
                    customer.readyTime(),
                    customer.dueTime()));
        }

        // Volume is inactive for these instances (decision D4), so capacity is effectively unbounded.
        final var vehicles = new ArrayList<VehicleSpec>(instance.vehicleCount());
        for (var v = 1; v <= instance.vehicleCount(); v++) {
            vehicles.add(new VehicleSpec(v, instance.capacity(), Long.MAX_VALUE, 0, instance.horizonMinutes(), 0));
        }

        final var matrix = DistanceMatrix.from(points, new EuclideanDistanceProvider());
        return new Loaded(instance.name(), new ProblemInstance(vehicles, stops, matrix, 0), points);
    }

    /** x maps to longitude, y to latitude — the wider bound takes the axis we do not control. */
    private static LatLon pointOf(double x, double y) {
        return LatLon.of(y, x);
    }

    public record Loaded(String name, ProblemInstance instance, List<LatLon> points) {}
}
