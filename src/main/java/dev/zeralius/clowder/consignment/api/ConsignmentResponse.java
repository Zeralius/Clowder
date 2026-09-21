package dev.zeralius.clowder.consignment.api;

import dev.zeralius.clowder.common.geo.LatLon;
import dev.zeralius.clowder.common.time.TimeWindow;
import dev.zeralius.clowder.consignment.domain.ConsignmentStatus;
import java.time.LocalDate;

/** Carries the value objects, so the JSON nests {@code location} and {@code window}. */
public record ConsignmentResponse(
        Long id,
        String reference,
        Long depotId,
        LocalDate serviceDate,
        LatLon location,
        String addressLine,
        long weightGrams,
        long volumeMillilitres,
        int serviceMinutes,
        TimeWindow window,
        ConsignmentStatus status) {}
