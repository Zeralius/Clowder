package dev.zeralius.clowder.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import dev.zeralius.clowder.security.domain.Role;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * A well-formed token signed with the wrong key.
 *
 * <p>This exists so that one test proves the signature is actually checked. Every other
 * authorisation test here mints a valid token and would pass identically against an application that
 * trusted whatever claims it was handed, which is the failure mode worth ruling out explicitly.
 */
final class ForgedTokens {

    /** A different secret from the one the build gives the application. That is the entire point. */
    private static final String WRONG_SECRET = "a-different-secret-that-the-application-does-not-know";

    private ForgedTokens() {}

    static String signedWithAnotherKey(String username, Role role) {
        final var key = new SecretKeySpec(WRONG_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        final var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));

        final var now = Instant.now();
        final var claims = JwtClaimsSet.builder()
                .issuer("clowder")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .subject(username)
                .claim("roles", List.of(role.name()))
                .build();

        return encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
