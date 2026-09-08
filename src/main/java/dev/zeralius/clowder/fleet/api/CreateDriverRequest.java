package dev.zeralius.clowder.fleet.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDriverRequest(
        @NotNull Long depotId,
        @NotBlank @Size(max = 32) String employeeNo,
        @NotBlank @Size(max = 128) String fullName,
        @NotBlank @Size(max = 16) String licenceClass) {}
