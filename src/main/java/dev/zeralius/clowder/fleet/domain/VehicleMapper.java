package dev.zeralius.clowder.fleet.domain;

import dev.zeralius.clowder.fleet.api.VehicleResponse;

public class VehicleMapper {

    private VehicleMapper() {}

    public static VehicleResponse toResponse(Vehicle vehicle) {
        var depot = vehicle.getDepot();
        return new VehicleResponse(
                vehicle.getId(),
                new VehicleResponse.DepotSummary(
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
