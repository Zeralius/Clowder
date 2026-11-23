package dev.zeralius.clowder.security;

import dev.zeralius.clowder.security.domain.Role;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Puts a caller in the security context for service-level tests.
 *
 * <p>A real {@link Jwt} rather than {@code @WithMockUser}, because the production code reads its
 * claims: a mock user carrying a username and an authority would exercise a path that does not
 * exist. The token is never signed or verified here — this is the context after a token has already
 * been accepted, which is exactly the state these tests are about.
 */
public final class TestAuth {

    private TestAuth() {}

    public static void asAdmin() {
        install("admin", Role.ADMIN, null);
    }

    public static void asDispatcher() {
        install("dispatcher", Role.DISPATCHER, null);
    }

    public static void asDriver(Long driverId) {
        install("driver-" + driverId, Role.DRIVER, driverId);
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void install(String username, Role role, Long driverId) {
        final var claims = new java.util.HashMap<String, Object>();
        claims.put("sub", username);
        claims.put("roles", List.of(role.name()));
        if (driverId != null) {
            claims.put("driverId", driverId);
        }

        final var jwt = new Jwt(
                "test-token-for-" + username,
                Instant.now(),
                Instant.now().plus(1, ChronoUnit.HOURS),
                Map.of("alg", "HS256"),
                claims);

        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, List.of(new SimpleGrantedAuthority(role.authority())), username));
    }
}
