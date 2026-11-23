package dev.zeralius.clowder.security.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TokenRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 200) String password) {

    /** Keeps the password out of logs, stack traces and anything else that calls toString. */
    @Override
    public String toString() {
        return "TokenRequest[username=%s, password=***]".formatted(username);
    }
}
