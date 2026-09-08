package dev.zeralius.clowder.fleet.api;

import java.math.BigDecimal;
import java.time.LocalTime;

public record VehicleResponse(
        Long id,
        DepotSummary depot,
        String registration,
        long capacityGrams,
        long capacityMillilitres,
        LocalTime shiftStart,
        int shiftMinutes,
        BigDecimal avgSpeedKph,
        boolean active
) {}
