package dev.zeralius.clowder.security;

import dev.zeralius.clowder.TestcontainersConfiguration;
import dev.zeralius.clowder.security.api.ChangePasswordRequest;
import dev.zeralius.clowder.security.domain.Role;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Tokens that stop working before they expire.
 *
 * <p>These go over HTTP rather than against the validator directly, and that is the point: the
 * mistake worth ruling out is not the comparison, it is the wiring. A decoder configured with the
 * revocation check in place of the default validators rather than alongside them would pass any unit
 * test of the comparison and silently stop checking expiry, so the question asked here is always
 * "does a request carrying this token get in?".
 *
 * <p>Every token is minted a measured distance from the cutoff rather than at it. {@code iat} is
 * whole seconds, so a token issued in the same second as a revocation is refused on purpose — see
 * {@code TokenRevocationValidator}. Testing at the boundary would be testing a sub-second race.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TestTokens.class})
@Sql("/db/testdata/depots.sql")
class TokenRevocationApiIT {

    @LocalServerPort
    int port;

    @Autowired
    TestTokens tokens;

    @Autowired
    JdbcTemplate jdbc;

    private RestTestClient rest;

    @BeforeEach
    void bindToTheRunningServer() {
        rest = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    /** Any authenticated role may read depots, so this asks the plainest question there is. */
    private RestTestClient.ResponseSpec readDepotsWith(String token) {
        return rest.get()
                .uri("/api/depots")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange();
    }

    /**
     * A token for this account as it would be issued right now. It cannot be back-dated: no token can
     * predate its own account, and the cutoff starts at the second the account was created.
     */
    private String freshTokenFor(String username) {
        return tokens.tokenFor(username, Role.DISPATCHER, null);
    }

    private String tokenIssuedInTwoSeconds(String username) {
        return tokens.tokenIssuedAt(
                username, Role.DISPATCHER, null, Instant.now().plusSeconds(2));
    }

    private void signOutAsAdmin(Long id) {
        rest.post()
                .uri("/api/users/" + id + "/sign-out")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForAdmin())
                .exchange()
                .expectStatus()
                .isNoContent();
    }

    private void deactivateAsAdmin(Long id) {
        rest.delete()
                .uri("/api/users/" + id)
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForAdmin())
                .exchange()
                .expectStatus()
                .isNoContent();
    }

    /** The baseline. Without it, every refusal below could be a token this test mints wrongly. */
    @Test
    void aTokenThatNothingHasRevokedWorks() {
        readDepotsWith(freshTokenFor("untouched")).expectStatus().isOk();
    }

    @Test
    void signingAnAccountOutRefusesTheTokenItIsHolding() {
        final var id = tokens.register("lost-phone", Role.DISPATCHER, null);
        final var token = freshTokenFor("lost-phone");
        readDepotsWith(token).expectStatus().isOk();

        signOutAsAdmin(id);

        readDepotsWith(token).expectStatus().isUnauthorized();
    }

    /** Signing out is not a lockout: the account is untouched and the next token is good. */
    @Test
    void aTokenIssuedAfterTheSignOutIsAccepted() {
        final var id = tokens.register("back-in", Role.DISPATCHER, null);
        signOutAsAdmin(id);

        readDepotsWith(tokenIssuedInTwoSeconds("back-in")).expectStatus().isOk();
    }

    /**
     * The gap this change closes. Deactivating used to stop the next sign-in and leave the token
     * already issued working for the rest of its eight hours.
     */
    @Test
    void deactivatingAnAccountStopsItsTokenAtOnce() {
        final var id = tokens.register("leaver", Role.DISPATCHER, null);
        final var token = freshTokenFor("leaver");
        readDepotsWith(token).expectStatus().isOk();

        deactivateAsAdmin(id);

        readDepotsWith(token).expectStatus().isUnauthorized();
    }

    /**
     * Why deactivating revokes as well as deactivating. Reactivated through SQL because there is no
     * endpoint for it — the point is that the account being active again is not enough to bring a
     * token issued before the deactivation back to life.
     */
    @Test
    void reactivatingDoesNotReviveTheOldTokens() {
        final var id = tokens.register("returner", Role.DISPATCHER, null);
        final var token = freshTokenFor("returner");
        deactivateAsAdmin(id);

        jdbc.update("update app_user set active = true where id = ?", id);

        readDepotsWith(token).expectStatus().isUnauthorized();
        readDepotsWith(tokenIssuedInTwoSeconds("returner")).expectStatus().isOk();
    }

    /** A password replaced because it leaked must not leave the session it leaked into alive. */
    @Test
    void changingThePasswordSignsTheOldTokensOut() {
        final var id = tokens.register("reset", Role.DISPATCHER, null);
        final var token = freshTokenFor("reset");

        rest.put()
                .uri("/api/users/" + id + "/password")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ChangePasswordRequest("a-brand-new-long-password"))
                .exchange()
                .expectStatus()
                .isOk();

        readDepotsWith(token).expectStatus().isUnauthorized();
    }

    /** The lost-phone case from the other side: no admin needed, and it takes this token down too. */
    @Test
    void aCallerCanSignThemselvesOutOfEveryDevice() {
        final var phone = freshTokenFor("two-devices");
        final var laptop = freshTokenFor("two-devices");

        rest.post()
                .uri("/api/auth/sign-out")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + laptop)
                .exchange()
                .expectStatus()
                .isNoContent();

        readDepotsWith(phone).expectStatus().isUnauthorized();
        readDepotsWith(laptop).expectStatus().isUnauthorized();
    }

    @Test
    void signingYourselfOutRequiresBeingSignedIn() {
        rest.post().uri("/api/auth/sign-out").exchange().expectStatus().isUnauthorized();
    }

    /** Signing somebody else out is an admin job, and the filter chain is where that is said. */
    @Test
    void aDispatcherCannotSignSomebodyElseOut() {
        final var id = tokens.register("somebody-else", Role.DISPATCHER, null);

        rest.post()
                .uri("/api/users/" + id + "/sign-out")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForDispatcher())
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    @Test
    void oneAccountSignedOutLeavesAnotherAlone() {
        final var id = tokens.register("revoked-one", Role.DISPATCHER, null);
        final var other = freshTokenFor("untouched-one");

        signOutAsAdmin(id);

        readDepotsWith(other).expectStatus().isOk();
    }

    /**
     * Fail closed. Only this application can sign a token and it only signs them for accounts that
     * exist, so this should be unreachable — which is why it is worth asserting rather than assuming.
     */
    @Test
    void aCorrectlySignedTokenForNoAccountIsRefused() {
        final var orphan = tokens.tokenForAnAccountThatDoesNotExist("never-existed", Role.ADMIN);

        readDepotsWith(orphan).expectStatus().isUnauthorized();
    }
}
