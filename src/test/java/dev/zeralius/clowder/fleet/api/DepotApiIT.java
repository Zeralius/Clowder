package dev.zeralius.clowder.fleet.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.TestcontainersConfiguration;
import dev.zeralius.clowder.common.web.PageResponse;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Exercises the API over real HTTP against the embedded server.
 *
 * <p>{@code RestTestClient.bindToServer()} rather than the auto-configured bean: the auto-configured
 * one binds to the {@code WebApplicationContext}, which is MockMvc underneath and never opens a
 * socket. The point of {@code RANDOM_PORT} is that the server handles each request on its own
 * transaction, so the test cannot wrap it — which is also why the seed script has to be idempotent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@Sql("/db/testdata/depots.sql")
class DepotApiIT {

    private static final ParameterizedTypeReference<PageResponse<DepotResponse>> DEPOT_PAGE =
            new ParameterizedTypeReference<>() {};

    @LocalServerPort
    int port;

    private RestTestClient rest;

    @BeforeEach
    void bindToTheRunningServer() {
        rest = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void healthEndpointIsUp() {
        rest.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"status\":\"UP\""));
    }

    @Test
    void returnsSeededDepotsAsAPage() {
        var page = rest.get()
                .uri("/api/depots")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(DEPOT_PAGE)
                .returnResult()
                .getResponseBody();

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).extracting(DepotResponse::code).containsExactlyInAnyOrder("MRL", "DTM");
    }

    @Test
    void createsADepotWithALocationHeader() {
        var request = new CreateDepotRequest(
                "ESS", "Essen Hub", new BigDecimal("51.4556"), new BigDecimal("7.0116"), "Europe/Berlin", null);

        var created = rest.post()
                .uri("/api/depots")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .valueMatches("Location", ".*/api/depots/\\d+")
                .expectBody(DepotResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created.code()).isEqualTo("ESS");
        assertThat(created.latitude()).isEqualByComparingTo("51.4556");
    }

    /** Pins the error envelope: every 404 is RFC 9457 with type, title, status and detail. */
    @Test
    void unknownDepotIsAProblemDetail() {
        var problem = rest.get()
                .uri("/api/depots/999999")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();

        assertThat(problem.getType()).hasToString("https://clowder.eu/errors/not-found");
        assertThat(problem.getTitle()).isEqualTo("Resource not found");
        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getDetail()).contains("Depot").contains("999999");
    }
}
