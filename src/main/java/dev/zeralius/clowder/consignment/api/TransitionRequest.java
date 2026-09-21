package dev.zeralius.clowder.consignment.api;

import dev.zeralius.clowder.consignment.domain.ConsignmentStatus;
import jakarta.validation.constraints.NotNull;

public record TransitionRequest(@NotNull ConsignmentStatus target) {}
