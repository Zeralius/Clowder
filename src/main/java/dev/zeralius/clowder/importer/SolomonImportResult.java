package dev.zeralius.clowder.importer;

import java.time.LocalDate;

public record SolomonImportResult(
        String instance,
        Long depotId,
        String depotCode,
        int vehiclesCreated,
        int consignmentsCreated,
        int horizonMinutes,
        LocalDate serviceDate) {}
