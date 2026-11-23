package dev.zeralius.clowder.security.domain;

import dev.zeralius.clowder.security.persistence.AppUserRepository;
import dev.zeralius.clowder.security.persistence.TokenValidity;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Refuses a token that is correctly signed and unexpired but should no longer work.
 *
 * <p><b>Why here rather than in a filter of its own.</b> "Has this token been revoked?" is a
 * question about the token, the same kind as "has it expired?", and a validator is where the decode
 * path already asks those. It also means the answer comes back the way an expired token's does — a
 * 401 with {@code WWW-Authenticate: Bearer error="invalid_token"} from the resource server — rather
 * than as a second, slightly different flavour of refusal invented here.
 *
 * <p><b>Three ways a live token dies.</b> The account was deactivated, the account's tokens were
 * revoked at some instant and this one predates it, or the account is gone. All three are checked on
 * every authenticated request against the database, which is the cost of revocation being immediate.
 * A cache in front of this read would make the one property the whole feature exists for into an
 * eventual one, so there is not one; if the read ever shows up in a profile, the answer is a cache
 * with a stated staleness bound and a note in the README, not a silent one.
 *
 * <p><b>An unknown subject is refused</b> rather than waved through. Only this application can mint
 * a signed token and it only mints them for accounts that exist, so a validly signed token naming
 * nobody should be impossible — which is exactly why it is worth refusing rather than assuming.
 *
 * <p><b>The caller is told nothing about which of the three it was.</b> Spring Security puts the
 * description into the {@code WWW-Authenticate} header, and "no such account" there would undo the
 * work the token endpoint does to avoid confirming who has an account. The reason goes to the log
 * instead.
 */
@Component
class TokenRevocationValidator implements OAuth2TokenValidator<Jwt> {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationValidator.class);

    /** One message for every reason, for the same reason the token endpoint has one. */
    private static final String REFUSAL = "The token is no longer valid";

    private final AppUserRepository users;

    TokenRevocationValidator(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        final var username = token.getSubject();
        final var issuedAt = token.getIssuedAt();
        if (username == null || issuedAt == null) {
            return refuse("a token with no subject or no issued-at claim cannot be checked", username);
        }

        final var account = users.findTokenValidityByUsername(username);
        if (account.isEmpty()) {
            return refuse("no account named '{}' exists", username);
        }
        if (!account.get().isActive()) {
            return refuse("the account '{}' is deactivated", username);
        }
        if (wasIssuedBeforeTheCutoff(issuedAt, account.get())) {
            return refuse("the tokens of '{}' were revoked after this one was issued", username);
        }

        return OAuth2TokenValidatorResult.success();
    }

    /**
     * {@code iat} is whole seconds — a JWT numeric date has no finer resolution — so the issue time
     * this can see is at or before the real one. Comparing it directly is therefore safe in the
     * direction that matters: a token genuinely issued before the cutoff always has an {@code iat}
     * before it too, so no revoked token survives.
     *
     * <p>The error is the other way and it is deliberate. A token issued in the same second as the
     * revocation is refused, because a second-precision claim cannot show that it came afterwards.
     * The cost is one retried sign-in by whoever just signed themselves out, inside a window shorter
     * than a second; the alternative is stamping tokens with an issue time slightly later than the
     * truth so that the arithmetic works out, which is a worse thing to have in an audit trail than
     * a retry.
     */
    private static boolean wasIssuedBeforeTheCutoff(Instant issuedAt, TokenValidity account) {
        return issuedAt.isBefore(account.getTokensValidFrom());
    }

    private static OAuth2TokenValidatorResult refuse(String reason, String username) {
        log.debug("Refusing a signed token: " + reason, username);
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, REFUSAL, null));
    }
}
