package dev.zeralius.clowder.fleet.domain;

import dev.zeralius.clowder.fleet.api.DepotSummary;
import dev.zeralius.clowder.fleet.api.DriverResponse;

/** Runs inside the transaction: {@code getDepot()} is a lazy proxy. */
final class DriverMapper {

    private DriverMapper() {}

    static DriverResponse toResponse(Driver driver) {
        var depot = driver.getDepot();
        return new DriverResponse(
                driver.getId(),
                new DepotSummary(depot.getId(), depot.getCode(), depot.getName()),
                driver.getEmployeeNo(),
                driver.getFullName(),
                driver.getLicenceClass(),
                driver.isActive());
    }
}
