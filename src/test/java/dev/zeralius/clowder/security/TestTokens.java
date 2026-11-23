package dev.zeralius.clowder.security;

import dev.zeralius.clowder.security.domain.Role;
import dev.zeralius.clowder.security.domain.SecurityProperties;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * Mints a real, signed token for an HTTP test.
 *
 * <p>It goes through the application own encoder, so the filter chain verifies the signature exactly
 * as it would in production — the decode path is under test here, not stubbed. What it skips is the
 * password check, because a test about who may publish a plan should not also be a test about bcrypt.
 * That path has {@code AuthApiIT} to itself.
 */
@TestComponent
public class TestTokens {

    private final JwtEncoder encoder;
    private final SecurityProperties properties;

    TestTokens(JwtEncoder encoder, SecurityProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public String bearerForDispatcher() {
        return "Bearer " + tokenFor("test-dispatcher", Role.DISPATCHER, null);
    }

    public String bearerForAdmin() {
        return "Bearer " + tokenFor("test-admin", Role.ADMIN, null);
    }

    public String bearerForDriver(Long driverId) {
        return "Bearer " + tokenFor("test-driver-" + driverId, Role.DRIVER, driverId);
    }

    public String tokenFor(String username, Role role, Long driverId) {
        final var now = Instant.now();
        final var builder = JwtClaimsSet.builder()
                .issuer("clowder")
                .issuedAt(now)
                .expiresAt(now.plus(properties.getTokenTtl()))
                .subject(username)
                .claim("roles", List.of(role.name()));
        if (driverId != null) {
            builder.claim("driverId", driverId);
        }
        final var claims = builder.build();

        return encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
