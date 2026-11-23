package dev.zeralius.clowder.security;

import dev.zeralius.clowder.security.domain.AppUser;
import dev.zeralius.clowder.security.domain.Role;
import dev.zeralius.clowder.security.domain.SecurityProperties;
import dev.zeralius.clowder.security.persistence.AppUserRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
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
 *
 * <p><b>It also creates the account.</b> Since tokens can be revoked, every request reads the row
 * behind the subject and refuses a token naming nobody — so a token minted for an account that was
 * never inserted is no longer a shortcut, it is a 401. The accounts it creates carry a hash that
 * matches nothing: they exist to be named by a token, and anything that wants to sign in with a
 * password should go through the token endpoint against the bootstrap admin.
 */
@TestComponent
public class TestTokens {

    /** Bcrypt-shaped and deliberately unmatchable. These accounts are not meant to be signed into. */
    private static final String UNUSABLE_HASH = "$2a$10$NoTestSignsInAsTheseAccountsSoThisMatchesNothingXXXXXXXXXXX";

    private final JwtEncoder encoder;
    private final SecurityProperties properties;
    private final AppUserRepository users;
    private final JdbcTemplate jdbc;

    TestTokens(JwtEncoder encoder, SecurityProperties properties, AppUserRepository users, JdbcTemplate jdbc) {
        this.encoder = encoder;
        this.properties = properties;
        this.users = users;
        this.jdbc = jdbc;
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
        return tokenIssuedAt(username, role, driverId, Instant.now());
    }

    /**
     * For the revocation tests, which need to say exactly where a token sits relative to a cutoff.
     *
     * <p>{@code iat} is whole seconds, so "now" is not a usable answer to "was this issued after
     * that revocation?" — a token minted in the same second as a revocation is refused on purpose.
     * These tests therefore ask the question a second or two away from the boundary rather than
     * racing it.
     */
    public String tokenIssuedAt(String username, Role role, Long driverId, Instant issuedAt) {
        register(username, role, driverId);
        return signed(username, role, driverId, issuedAt, issuedAt.plus(properties.getTokenTtl()));
    }

    /**
     * Pins that adding a revocation check did not quietly replace the expiry check.
     *
     * <p>The account is back-dated along with the token, through SQL, so that the <em>only</em> thing
     * wrong with this token is its expiry. Without that it would also predate its account's
     * revocation cutoff, be refused as revoked, and prove nothing about {@code exp} — which is the
     * failure the test exists to catch.
     */
    public String expiredTokenFor(String username, Role role) {
        register(username, role, null);
        final var issuedAt = Instant.now().minusSeconds(7200);
        backdateTheRevocationCutoff(username, issuedAt.minusSeconds(60));
        return signed(username, role, null, issuedAt, issuedAt.plusSeconds(3600));
    }

    /** For tests that need a token older than the account could really have issued one. */
    public void backdateTheRevocationCutoff(String username, Instant cutoff) {
        jdbc.update(
                "update app_user set tokens_valid_from = ? where username = ?",
                OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC),
                username);
    }

    /** Correctly signed, names nobody. The one case the validator should refuse outright. */
    public String tokenForAnAccountThatDoesNotExist(String username, Role role) {
        final var now = Instant.now();
        return signed(username, role, null, now, now.plus(properties.getTokenTtl()));
    }

    /** @return the account id, which the admin endpoints are addressed by */
    public Long register(String username, Role role, Long driverId) {
        return users.findByUsername(username)
                .orElseGet(() -> users.save(new AppUser(username, UNUSABLE_HASH, role, driverId)))
                .getId();
    }

    private String signed(String username, Role role, Long driverId, Instant issuedAt, Instant expiresAt) {
        final var builder = JwtClaimsSet.builder()
                .issuer("clowder")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(username)
                .claim("roles", List.of(role.name()));
        if (driverId != null) {
            builder.claim("driverId", driverId);
        }

        return encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), builder.build()))
                .getTokenValue();
    }
}
