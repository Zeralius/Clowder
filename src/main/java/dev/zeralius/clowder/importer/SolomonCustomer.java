package dev.zeralius.clowder.importer;

/**
 * One row of a Solomon instance. Customer 0 is the depot.
 *
 * <p>Coordinates are {@code double} because they are abstract plane units that only ever feed
 * Euclidean distance — this is the one place in the system where a floating-point coordinate is
 * correct. Everything that accumulates (demand, times) is integral.
 */
public record SolomonCustomer(
        int id, double x, double y, long demand, int readyTime, int dueTime, int serviceTime) {}
