package dev.zeralius.clowder.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.TestcontainersConfiguration;
import dev.zeralius.clowder.security.api.TokenRequest;
import dev.zeralius.clowder.security.domain.LoginRateLimiter;
import org.junit.jupiter.api.AfterEach;
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
 * The rate limit over HTTP, against the shipped budgets.
 *
 * <p>The unit test owns the arithmetic. What can only be checked here is that the refusal comes back
 * as a 429 in the same RFC 9457 envelope as every other error, with a {@code Retry-After} a client
 * can act on, and that it reveals no more than the 401 it replaces.
 *
 * <p>Every request in this class comes from 127.0.0.1, so one address carries all of it — which is
 * also why the limiter is cleared before and after each test. The Spring context is shared with the
 * other security tests, and in-memory counters outlive a test method.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TestTokens.class})
@Sql("/db/testdata/depots.sql")
class TokenRateLimitApiIT {

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "test-only-bootstrap-password";
    private static final int BUDGET = 5;

    @LocalServerPort
    int port;

    @Autowired
    LoginRateLimiter limiter;

    private RestTestClient rest;

    @BeforeEach
    void bindToTheRunningServer() {
        limiter.clear();
        rest = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @AfterEach
    void leaveNoCountsBehind() {
        limiter.clear();
    }

    private RestTestClient.ResponseSpec attempt(String username, String password) {
        return rest.post()
                .uri("/api/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new TokenRequest(username, password))
                .exchange();
    }

    private void exhaustTheBudgetFor(String username) {
        for (var i = 0; i < BUDGET; i++) {
            attempt(username, "wrong-password-" + i).expectStatus().isUnauthorized();
        }
    }

    @Test
    void theAttemptAfterTheBudgetIsRefusedWithARetryAfter() {
        exhaustTheBudgetFor(ADMIN);

        final var refused = attempt(ADMIN, "wrong-password-again")
                .expectStatus()
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader()
                .exists(HttpHeaders.RETRY_AFTER)
                .expectBody(String.class)
                .returnResult();

        assertThat(Integer.parseInt(refused.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER)))
                .as("a client that honours this waits rather than hammering")
                .isPositive();
    }

    /** Same envelope as every other error, and it still says nothing about who has an account. */
    @Test
    void theRefusalIsAProblemDocumentThatRevealsNothing() {
        exhaustTheBudgetFor("nobody-by-that-name");

        final var body = attempt("nobody-by-that-name", "another-guess")
                .expectStatus()
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).contains("Too many requests").contains("too-many-requests");
        assertThat(body).doesNotContain("exist").doesNotContain("unknown").doesNotContain("password\"");
    }

    /**
     * An unknown username is throttled exactly like a real one. If it were not, how soon the endpoint
     * started refusing would answer the question the single failure message exists to refuse.
     */
    @Test
    void anUnknownUsernameIsThrottledLikeARealOne() {
        exhaustTheBudgetFor("nobody-by-that-name");

        attempt("nobody-by-that-name", "another-guess").expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * The price of the budget, stated rather than hidden: once this client has spent it, the real
     * password is refused too for the rest of the window. That is why the budget is keyed on the
     * address as well as the username — somewhere else, the same account is unaffected, which the
     * unit test pins down.
     */
    @Test
    void theRightPasswordIsAlsoRefusedWhileTheBudgetIsSpent() {
        exhaustTheBudgetFor(ADMIN);

        attempt(ADMIN, ADMIN_PASSWORD).expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void gettingItRightBeforeTheBudgetRunsOutClearsTheCount() {
        for (var i = 0; i < BUDGET - 1; i++) {
            attempt(ADMIN, "wrong-password-" + i).expectStatus().isUnauthorized();
        }

        attempt(ADMIN, ADMIN_PASSWORD).expectStatus().isOk();

        for (var i = 0; i < BUDGET - 1; i++) {
            attempt(ADMIN, "wrong-password-" + i).expectStatus().isUnauthorized();
        }
        attempt(ADMIN, ADMIN_PASSWORD).expectStatus().isOk();
    }

    /** Clearing the counts is the lever for an office locked out by a misconfigured client. */
    @Test
    void clearingTheCountsLetsThemInAgain() {
        exhaustTheBudgetFor(ADMIN);
        attempt(ADMIN, ADMIN_PASSWORD).expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        limiter.clear();

        attempt(ADMIN, ADMIN_PASSWORD).expectStatus().isOk();
    }
}
