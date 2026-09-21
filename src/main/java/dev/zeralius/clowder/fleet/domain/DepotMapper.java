package dev.zeralius.clowder.fleet.domain;

import dev.zeralius.clowder.fleet.api.DepotResponse;

final class DepotMapper {

    private DepotMapper() {}

    static DepotResponse toResponse(Depot depot) {
        return new DepotResponse(
                depot.getId(),
                depot.getCode(),
                depot.getName(),
                depot.getLatitude(),
                depot.getLongitude(),
                depot.getTimezone()
        );
    }
}
