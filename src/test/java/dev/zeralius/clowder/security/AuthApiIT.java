package dev.zeralius.clowder.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.TestcontainersConfiguration;
import dev.zeralius.clowder.security.api.CreateUserRequest;
import dev.zeralius.clowder.security.api.TokenRequest;
import dev.zeralius.clowder.security.api.TokenResponse;
import dev.zeralius.clowder.security.api.UserResponse;
import dev.zeralius.clowder.security.domain.LoginRateLimiter;
import dev.zeralius.clowder.security.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Getting in, and not getting in.
 *
 * <p>The bootstrap admin is created once when the application starts against an empty user table,
 * with the password the build passes in, so this authenticates as a real account through the real
 * password check rather than minting a token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TestTokens.class})
@Sql("/db/testdata/depots.sql")
class AuthApiIT {

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "test-only-bootstrap-password";

    @LocalServerPort
    int port;

    @Autowired
    TestTokens tokens;

    @Autowired
    LoginRateLimiter limiter;

    private RestTestClient rest;

    @BeforeEach
    void bindToTheRunningServer() {
        // Several tests here sign in wrongly on purpose, and the failure counts are in memory and
        // shared with every other test in this context. Without this, these tests pass or fail
        // depending on what ran before them.
        limiter.clear();

        rest = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private TokenResponse login(String username, String password) {
        return rest.post()
                .uri("/api/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new TokenRequest(username, password))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(TokenResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private void expectUnauthorized(String username, String password) {
        rest.post()
                .uri("/api/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new TokenRequest(username, password))
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void theBootstrapAdminCanGetAToken() {
        final var response = login(ADMIN, ADMIN_PASSWORD);

        assertThat(response.token()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.role()).isEqualTo(Role.ADMIN);
        assertThat(response.driverId()).isNull();
        assertThat(response.expiresAt()).isNotNull();
    }

    @Test
    void aWrongPasswordIsRefused() {
        expectUnauthorized(ADMIN, "definitely-not-the-password");
    }

    /** Same answer for a user that does not exist, so the endpoint cannot enumerate accounts. */
    @Test
    void anUnknownUserIsRefusedTheSameWay() {
        expectUnauthorized("nobody-by-that-name", ADMIN_PASSWORD);
    }

    @Test
    void aRequestWithNoTokenIsUnauthorized() {
        rest.get().uri("/api/depots").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void aGarbageTokenIsUnauthorized() {
        rest.get()
                .uri("/api/depots")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    /**
     * A token signed with the wrong key is refused, which is the whole point of signing one. Without
     * this, every other authorisation test would pass just as happily against a forged token.
     */
    @Test
    void aTokenSignedWithAnotherKeyIsUnauthorized() {
        final var forged = ForgedTokens.signedWithAnotherKey("admin", Role.ADMIN);

        rest.get()
                .uri("/api/depots")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + forged)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    /**
     * An expired token is still refused, and this is not as obvious a test as it looks.
     *
     * <p>Revocation is checked by a validator handed to the decoder, and installing one
     * <em>replaces</em> the validators the decoder came with. Get that wrong and expiry stops being
     * checked at all, with no symptom until a token outlives its {@code exp} in production. This is
     * the test that fails instead.
     */
    @Test
    void anExpiredTokenIsUnauthorized() {
        final var expired = tokens.expiredTokenFor("expired-admin", Role.ADMIN);

        rest.get()
                .uri("/api/depots")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void theHealthEndpointNeedsNoToken() {
        rest.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void anAdminCanCreateADispatcherWhoCanThenLogIn() {
        final var created = rest.post()
                .uri("/api/users")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateUserRequest("dolores", "a-long-enough-password", Role.DISPATCHER, null))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(UserResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created.role()).isEqualTo(Role.DISPATCHER);
        assertThat(created.driverId()).isNull();
        assertThat(created.active()).isTrue();

        assertThat(login("dolores", "a-long-enough-password").role()).isEqualTo(Role.DISPATCHER);
    }

    @Test
    void aShortPasswordIsRefused() {
        rest.post()
                .uri("/api/users")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateUserRequest("mallory", "short", Role.DISPATCHER, null))
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    /** A driver account with no driver is an account that could authenticate and then own nothing. */
    @Test
    void aDriverAccountWithoutADriverIsRefused() {
        rest.post()
                .uri("/api/users")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateUserRequest("ghost", "a-long-enough-password", Role.DRIVER, null))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void aDeactivatedAccountCannotGetAToken() {
        final var created = rest.post()
                .uri("/api/users")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateUserRequest("leaving", "a-long-enough-password", Role.DISPATCHER, null))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(UserResponse.class)
                .returnResult()
                .getResponseBody();

        rest.delete()
                .uri("/api/users/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForAdmin())
                .exchange()
                .expectStatus()
                .isNoContent();

        expectUnauthorized("leaving", "a-long-enough-password");
    }

    /** The response never mentions the password, in any form. */
    @Test
    void aUserResponseCarriesNothingAboutThePassword() {
        final var body = rest.get()
                .uri("/api/users")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForAdmin())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).doesNotContain("password").doesNotContain("hash").doesNotContain("$2a$");
    }
}
