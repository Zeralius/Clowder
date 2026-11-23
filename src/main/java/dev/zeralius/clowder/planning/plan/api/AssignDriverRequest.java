package dev.zeralius.clowder.planning.plan.api;

import jakarta.validation.constraints.NotNull;

public record AssignDriverRequest(@NotNull Long driverId) {}
