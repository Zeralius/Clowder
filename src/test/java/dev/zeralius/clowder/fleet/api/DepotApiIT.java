package dev.zeralius.clowder.fleet.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
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
    void returnsSeededDepots() {
        var depots = rest.get()
                .uri("/api/depots")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(DepotResponse[].class)
                .returnResult()
                .getResponseBody();

        assertThat(depots).extracting(DepotResponse::code).containsExactlyInAnyOrder("MRL", "DTM");
    }
}
