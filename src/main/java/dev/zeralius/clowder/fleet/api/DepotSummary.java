package dev.zeralius.clowder.fleet.api;

/** The slice of a depot that other resources embed. Not the full {@link DepotResponse}. */
public record DepotSummary(Long id, String code, String name) {}
