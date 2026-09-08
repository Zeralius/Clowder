package dev.zeralius.clowder.fleet.api;

public record DriverResponse(
        Long id, DepotSummary depot, String employeeNo, String fullName, String licenceClass, boolean active) {}
