package dev.zeralius.clowder.planning.solver;

import dev.zeralius.clowder.common.geo.EuclideanDistanceProvider;
import dev.zeralius.clowder.common.geo.LatLon;
import dev.zeralius.clowder.planning.model.DistanceMatrix;
import dev.zeralius.clowder.planning.model.ProblemInstance;
import dev.zeralius.clowder.planning.model.StopSpec;
import dev.zeralius.clowder.planning.model.VehicleSpec;
import java.util.ArrayList;
import java.util.List;

/**
 * Fixtures for the solver tests. The guide refers to {@code TestInstances.line()} without defining it;
 * this is that class.
 */
final class TestInstances {

    static final long UNIT = 1_000_000L;
    static final int DEPOT = 0;

    private TestInstances() {}

    /**
     * The M2 line — depot at 0, stops at 10, 30 and 60 — with windows that leave every stop a finite,
     * distinct amount of slack: [0,100], [0,150], [0,200], ten minutes of service, a 400-minute shift.
     * Two roomy vehicles. Route {0, 1, 2} serves at 10, 40 and 80 and is back at 150.
     */
    static ProblemInstance line() {
        var matrix = DistanceMatrix.from(
                List.of(LatLon.of(0d, 0d), LatLon.of(0d, 10d), LatLon.of(0d, 30d), LatLon.of(0d, 60d)),
                new EuclideanDistanceProvider());
        var stops = List.of(
                new StopSpec(100L, 1, 0L, 0L, 10, 0, 100),
                new StopSpec(200L, 2, 0L, 0L, 10, 0, 150),
                new StopSpec(300L, 3, 0L, 0L, 10, 0, 200));
        var vehicles = List.of(
                new VehicleSpec(1L, 100_000L, 100_000L, 0, 400, DEPOT),
                new VehicleSpec(2L, 100_000L, 100_000L, 0, 400, DEPOT));
        return new ProblemInstance(vehicles, stops, matrix, DEPOT);
    }

    /**
     * Two tight clusters of four stops each, far apart, 30 g per stop against 100 g vehicles: three
     * stops per vehicle, so eight stops need at least three vehicles. Four are offered, so the solver
     * has one to give back.
     */
    static ProblemInstance twoClusters() {
        var points = new ArrayList<LatLon>();
        points.add(LatLon.of(0d, 0d));
        for (var cluster : new double[] {10d, 50d}) {
            points.add(LatLon.of(cluster, cluster));
            points.add(LatLon.of(cluster + 2, cluster));
            points.add(LatLon.of(cluster, cluster + 2));
            points.add(LatLon.of(cluster + 2, cluster + 2));
        }
        var matrix = DistanceMatrix.from(points, new EuclideanDistanceProvider());

        var stops = new ArrayList<StopSpec>();
        for (var i = 1; i <= 8; i++) {
            stops.add(new StopSpec(i * 100L, i, 30L, 0L, 5, 0, 1000));
        }
        var vehicles = new ArrayList<VehicleSpec>();
        for (var v = 1; v <= 4; v++) {
            vehicles.add(new VehicleSpec(v, 100L, Long.MAX_VALUE, 0, 1000, DEPOT));
        }
        return new ProblemInstance(vehicles, stops, matrix, DEPOT);
    }

    /** A solution from explicit routes, with distances computed, for seeding a {@code SolutionState}. */
    static Solution solution(ProblemInstance instance, int[]... routesByVehicle) {
        var routes = new ArrayList<Route>();
        var total = 0L;
        for (var v = 0; v < routesByVehicle.length; v++) {
            var vehicle = instance.vehicles().get(v);
            var route = routesByVehicle[v];
            var distance = RouteCalculator.distance(route, route.length, vehicle, instance);
            var duration = RouteCalculator.duration(route, route.length, vehicle, instance);
            routes.add(new Route(vehicle.id(), route, distance, duration));
            total += distance;
        }
        return new Solution(routes, new int[0], total);
    }
}
