package dev.zeralius.clowder.security.domain;

import dev.zeralius.clowder.common.error.ConflictException;
import dev.zeralius.clowder.security.api.TokenRequest;
import dev.zeralius.clowder.security.api.TokenResponse;
import dev.zeralius.clowder.security.persistence.AppUserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exchanges a username and password for a bearer token.
 *
 * <p><b>One failure message for every failure.</b> No such user, wrong password and deactivated
 * account all answer "invalid username or password". Telling a caller which one it was turns the
 * endpoint into a tool for discovering who has an account here.
 *
 * <p>The password is still hashed and compared even when the user does not exist, against a hash that
 * cannot match. Otherwise the endpoint answers measurably faster for unknown usernames, and a
 * timing difference is an answer.
 */
@Service
public class TokenService {

    /**
     * A valid bcrypt hash of a value nothing will ever submit, used to spend the same time on a
     * missing user as on a real one.
     */
    private static final String ABSENT_USER_HASH = "$2a$10$ThisIsNotARealHashAndWillNeverMatchAnyPasswordXXXXXXXXXXX";

    private static final String ISSUER = "clowder";

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;

    public TokenService(
            AppUserRepository users,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            SecurityProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public TokenResponse issue(TokenRequest request) {
        final var found = users.findByUsername(request.username());

        final var hash = found.map(AppUser::getPasswordHash).orElse(ABSENT_USER_HASH);
        final var passwordMatches = passwordEncoder.matches(request.password(), hash);

        if (found.isEmpty() || !passwordMatches || !found.get().isActive()) {
            throw new BadCredentialsException("Invalid username or password");
        }

        return tokenFor(found.get());
    }

    private TokenResponse tokenFor(AppUser user) {
        final var issuedAt = Instant.now();
        final var expiresAt = issuedAt.plus(properties.getTokenTtl());

        final var builder = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .claim("roles", List.of(user.getRole().name()));

        // Only when there is one. JwtClaimsSet rejects a null claim value outright, so adding this
        // unconditionally means no dispatcher or admin could ever be issued a token at all.
        //
        // The driver id travels in the token so the ownership check is one database read rather than
        // two. It is signed, so it is no more forgeable than the role beside it.
        if (user.getDriverId() != null) {
            builder.claim("driverId", user.getDriverId());
        }

        final var claims = builder.build();

        final var header = JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                .build();
        final var token =
                jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new TokenResponse(token, "Bearer", expiresAt, user.getRole(), user.getDriverId());
    }

    /** Hashing lives here, with the encoder, so no caller can store a plaintext by accident. */
    public String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 12) {
            throw new ConflictException("A password must be at least 12 characters");
        }
        return passwordEncoder.encode(rawPassword);
    }
}
