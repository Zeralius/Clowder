package dev.zeralius.clowder.security.api;

import dev.zeralius.clowder.security.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param password minimum length is enforced here and again in the service, because the service is
 *     also reachable from the bootstrap path where no request object exists
 * @param driverId required for {@link Role#DRIVER} and refused for any other role
 */
public record CreateUserRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(min = 12, max = 200) String password,
        @NotNull Role role,
        Long driverId) {

    @Override
    public String toString() {
        return "CreateUserRequest[username=%s, role=%s, driverId=%s, password=***]".formatted(username, role, driverId);
    }
}
