package dev.zeralius.clowder.planning.plan.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

/**
 * @param budgetSeconds wall-clock limit for local search; the solver stops when it expires, so this
 *     bounds the request rather than the problem size
 */
public record PlanRunRequest(
        @NotNull Long depotId,
        @NotNull LocalDate serviceDate,
        @Positive @Max(60) Integer budgetSeconds,
        Long seed) {}
