package dev.zeralius.clowder.security.domain;

import dev.zeralius.clowder.common.error.ConflictException;
import dev.zeralius.clowder.security.AuthenticatedUser;
import dev.zeralius.clowder.security.CurrentUser;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Reads the caller out of the security context, so that no other module has to know how a claim is
 * spelled or that Spring Security exists at all.
 *
 * <p>There is one way in: a signed bearer token. No HTTP Basic, which would be a second
 * authentication path to keep correct for the convenience of typing a password into curl, and the
 * token endpoint is one request away.
 */
@Service
class JwtCurrentUser implements CurrentUser {

    @Override
    public AuthenticatedUser require() {
        final var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            // Not a client error: an endpoint that reaches this was left out of the filter chain.
            throw new ConflictException("No authenticated caller for a request that requires one");
        }
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ConflictException("Authenticated with %s, which carries no claims"
                    .formatted(authentication.getPrincipal().getClass().getSimpleName()));
        }

        return new AuthenticatedUser(jwt.getSubject(), roleOf(jwt), driverIdOf(jwt));
    }

    private static Role roleOf(Jwt jwt) {
        final var roles = jwt.getClaimAsStringList("roles");
        if (roles == null || roles.isEmpty()) {
            throw new ConflictException("Token carries no role");
        }
        return Role.valueOf(roles.getFirst());
    }

    private static Long driverIdOf(Jwt jwt) {
        return jwt.getClaim("driverId") instanceof Number number ? number.longValue() : null;
    }
}
