package dev.zeralius.clowder.security.domain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may call what.
 *
 * <p>One way in: a signed bearer token. No HTTP Basic, which would be a second authentication path
 * to keep correct in exchange for being able to type a password into curl.
 *
 * <p><b>Why a bearer token and not sessions.</b> The clients are a dispatcher console and a driver
 * app on a phone that spends half its day with no signal and retries everything. A stateless token
 * means a retry needs nothing on the server to still exist, which is the same reason the event log is
 * idempotent. {@code SessionCreationPolicy.STATELESS} is not an optimisation here, it is the model.
 *
 * <p><b>Why CSRF is disabled.</b> There is no cookie and no form: a browser cannot be tricked into
 * attaching a bearer token it does not hold. Disabling it on a session-based API would be a hole;
 * here leaving it on would only break every client for no benefit.
 *
 * <p><b>A stateless token that can still be revoked.</b> The decoder asks
 * {@link TokenRevocationValidator} on every request whether the account behind a token is still
 * active and whether the token predates a revocation. That is a database read per request, which is
 * the price of a deactivated account losing access at once rather than in eight hours; see that
 * class for why it is paid rather than cached.
 *
 * <p><b>The URL rules are coarse on purpose.</b> They say which role may reach which resource, and
 * nothing about ownership, because "a driver may only report against their own plans" is not a
 * property of the URL — the plan id is in the path and the answer is in the database. That check
 * lives in the service that knows, and a rule here that looked like it covered it would be worse than
 * no rule at all.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfiguration {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecurityProperties properties;

    SecurityConfiguration(SecurityProperties properties) {
        this.properties = properties;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Getting a token cannot require one, and liveness checks are for the
                        // orchestrator rather than for a person.
                        .requestMatchers("/api/auth/token")
                        .permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info")
                        .permitAll()

                        // Reference data: admins write it, everyone authenticated may read it. A
                        // driver needs to see the depot they are leaving from.
                        .requestMatchers(HttpMethod.GET, "/api/depots/**", "/api/vehicles/**", "/api/drivers/**")
                        .authenticated()
                        .requestMatchers("/api/depots/**", "/api/vehicles/**", "/api/drivers/**")
                        .hasRole(Role.ADMIN.name())
                        .requestMatchers("/api/users/**")
                        .hasRole(Role.ADMIN.name())

                        // Planning is the dispatcher job. Admins too, so that a locked-out
                        // dispatcher is a support call rather than an outage.
                        .requestMatchers("/api/plan-runs/**", "/api/import/**")
                        .hasAnyRole(Role.DISPATCHER.name(), Role.ADMIN.name())
                        .requestMatchers(HttpMethod.POST, "/api/plans/*/publish", "/api/plans/*/driver")
                        .hasAnyRole(Role.DISPATCHER.name(), Role.ADMIN.name())
                        .requestMatchers("/api/consignments/**")
                        .hasAnyRole(Role.DISPATCHER.name(), Role.ADMIN.name())

                        // Reporting and reading a plan are open to any authenticated role; which
                        // plan is whose is decided in the service, not here.
                        .requestMatchers("/api/plans/**")
                        .authenticated()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(converter())));

        return http.build();
    }

    /**
     * Roles travel in a {@code roles} claim rather than the default {@code scope}, because they are
     * roles and calling them scopes would mean every rule above had to say {@code hasAuthority} with
     * a {@code SCOPE_} prefix and mean something subtly different.
     */
    private static JwtAuthenticationConverter converter() {
        final var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        final var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

    /**
     * The decoder checks the signature, then the claims, and revocation is one of the claim checks.
     *
     * <p>{@code createDefaultWithValidators} rather than passing the revocation validator on its own:
     * {@code setJwtValidator} <em>replaces</em> the validator the builder installed, so handing it one
     * validator would quietly stop expiry being checked at all. That is a one-line mistake with no
     * symptom until a token outlives its {@code exp}, which is why {@code AuthApiIT} asks an expired
     * token for a depot and expects to be turned away.
     */
    @Bean
    JwtDecoder jwtDecoder(TokenRevocationValidator revocation) {
        final var decoder = NimbusJwtDecoder.withSecretKey(secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(revocation));
        return decoder;
    }

    /**
     * Fails fast and loudly. An application that starts with no signing secret is an application
     * whose tokens anyone can mint, and finding that out at startup beats finding it out later.
     */
    private SecretKeySpec secretKey() {
        final var secret = properties.getJwtSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "clowder.security.jwt-secret is not set. There is no default: set it to at least "
                            + MIN_SECRET_BYTES + " random bytes, from the environment rather than a file.");
        }
        final var bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("clowder.security.jwt-secret must be at least " + MIN_SECRET_BYTES
                    + " bytes for HS256; it is " + bytes.length);
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
