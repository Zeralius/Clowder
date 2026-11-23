package dev.zeralius.clowder.fleet.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDriverRequest(
        @NotBlank @Size(max = 128) String fullName,
        @NotBlank @Size(max = 16) String licenceClass) {}
