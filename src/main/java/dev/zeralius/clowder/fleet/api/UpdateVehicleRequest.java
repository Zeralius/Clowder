package dev.zeralius.clowder.fleet.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalTime;

public record UpdateVehicleRequest(
        @Positive long capacityGrams,
        @Positive long capacityMillilitres,
        @NotNull LocalTime shiftStart,
        @Positive @Max(10080) int shiftMinutes,
        @NotNull @DecimalMin("0.1") BigDecimal avgSpeedKph) {}
