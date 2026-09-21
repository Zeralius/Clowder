package dev.zeralius.clowder.importer;

import java.util.List;

/** A parsed instance. Pure data: no Spring, no persistence, no behaviour beyond its own invariants. */
public record SolomonInstance(
        String name, int vehicleCount, long capacity, SolomonCustomer depot, List<SolomonCustomer> customers) {

    public SolomonInstance {
        customers = List.copyOf(customers);
    }

    /** The planning horizon: how long a vehicle may be out, in minutes. */
    public int horizonMinutes() {
        return depot.dueTime();
    }
}
