package dev.zeralius.clowder.consignment.domain;

import dev.zeralius.clowder.consignment.api.ConsignmentResponse;

final class ConsignmentMapper {

    private ConsignmentMapper() {}

    static ConsignmentResponse toResponse(Consignment consignment) {
        return new ConsignmentResponse(
                consignment.getId(),
                consignment.getReference(),
                consignment.getDepotId(),
                consignment.getServiceDate(),
                consignment.getLocation(),
                consignment.getAddressLine(),
                consignment.getWeightGrams(),
                consignment.getVolumeMillilitres(),
                consignment.getServiceMinutes(),
                consignment.getWindow(),
                consignment.getStatus());
    }
}
