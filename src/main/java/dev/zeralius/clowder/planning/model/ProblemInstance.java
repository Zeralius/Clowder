package dev.zeralius.clowder.planning.model;

import java.util.List;

/**
 * Everything a planning run needs, flat and immutable. Built once at the edge of a planning run from
 * entities; the evaluator and solver never see a JPA entity.
 *
 * <p>Routes are expressed as arrays of positions in {@link #stops()}; each stop then carries its own
 * {@link StopSpec#index()} into the matrix.
 *
 * @param depotIndex the matrix row shared by every vehicle's depot in a single-depot instance
 */
public record ProblemInstance(
        List<VehicleSpec> vehicles, List<StopSpec> stops, DistanceMatrix matrix, int depotIndex) {

    public ProblemInstance {
        vehicles = List.copyOf(vehicles);
        stops = List.copyOf(stops);
    }

    public StopSpec stopAt(int position) {
        return stops.get(position);
    }

    public int stopCount() {
        return stops.size();
    }
}
