package dev.zeralius.clowder.fleet.domain;

import dev.zeralius.clowder.fleet.api.DepotSummary;
import dev.zeralius.clowder.fleet.api.VehicleResponse;

final class VehicleMapper {

    private VehicleMapper() {}

    static VehicleResponse toResponse(Vehicle vehicle) {
        var depot = vehicle.getDepot();
        return new VehicleResponse(
                vehicle.getId(),
                new DepotSummary(
                        depot.getId(),
                        depot.getCode(),
                        depot.getName()
                ),
                vehicle.getRegistration(),
                vehicle.getCapacityGrams(),
                vehicle.getCapacityMillilitres(),
                vehicle.getShiftStart(),
                vehicle.getShiftMinutes(),
                vehicle.getAvgSpeedKph(),
                vehicle.isActive()
        );

    }
}
