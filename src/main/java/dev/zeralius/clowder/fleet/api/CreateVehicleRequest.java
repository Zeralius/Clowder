package dev.zeralius.clowder.fleet.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalTime;

public record CreateVehicleRequest(
        @NotNull Long depotId,
        @NotBlank @Size(max = 16) String registration,
        @Positive long capacityGrams,
        @Positive long capacityMillilitres,
        @NotNull LocalTime shiftStart,
        @Positive @Max(10080) int shiftMinutes,

        @NotNull @DecimalMin("0.1") @Digits(integer = 3, fraction = 2)
        BigDecimal avgSpeedKph) {}
