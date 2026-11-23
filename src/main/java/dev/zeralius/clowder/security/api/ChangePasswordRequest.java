package dev.zeralius.clowder.security.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(min = 12, max = 200) String password) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[password=***]";
    }
}
