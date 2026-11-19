package dev.zeralius.clowder.planning.plan;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.TestcontainersConfiguration;
import dev.zeralius.clowder.importer.SolomonImportService;
import dev.zeralius.clowder.planning.plan.api.PlanRunRequest;
import dev.zeralius.clowder.planning.plan.api.PlanRunResponse;
import dev.zeralius.clowder.planning.plan.api.PlannedRouteResponse;
import dev.zeralius.clowder.planning.plan.api.PlannedStopResponse;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The end-to-end path this project exists for: data in through the import API, a plan out through
 * the planning API. Until M4 persists plan runs, this is also the only proof that the solver is
 * reachable from outside its own package.
 *
 * <p>Not {@code @Transactional}: the import must commit before the server's own request thread can
 * see it, same reason {@code DepotApiIT} cannot wrap its requests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@Sql("/db/testdata/depots.sql")
class PlanRunApiIT {

    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 1, 5);

    @Autowired
    SolomonImportService importer;

    @LocalServerPort
    int port;

    private RestTestClient rest;

    @BeforeEach
    void bindToTheRunningServer() {
        rest = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private static Reader fixture() {
        var stream = Objects.requireNonNull(
                PlanRunApiIT.class.getResourceAsStream("/solomon/MINI.txt"), "fixture missing");
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }

    private PlanRunResponse planImportedInstance(Integer budgetSeconds) {
        var imported = importer.importFrom(fixture(), SERVICE_DATE);

        return rest.post()
                .uri("/api/plan-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PlanRunRequest(imported.depotId(), SERVICE_DATE, budgetSeconds, null))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PlanRunResponse.class)
                .returnResult()
                .getResponseBody();
    }

    @Test
    void importsAnInstanceAndPlansItOverHttp() {
        var plan = planImportedInstance(2);

        assertThat(plan.consignmentCount()).isEqualTo(3);
        assertThat(plan.vehiclesAvailable()).isEqualTo(3);
        assertThat(plan.vehiclesUsed()).isBetween(1, 3);
        assertThat(plan.unassignedReferences()).isEmpty();
        assertThat(plan.totalDistanceUnits()).isPositive();
        assertThat(plan.solverVersion()).isNotBlank();

        assertThat(plan.routes()).isNotEmpty();
        assertThat(plan.routes()).allSatisfy(route -> {
            assertThat(route.stopCount()).isPositive();
            assertThat(route.stops()).hasSize(route.stopCount());
        });

        var planned = plan.routes().stream()
                .flatMap(r -> r.stops().stream())
                .map(PlannedStopResponse::reference)
                .toList();
        assertThat(planned).containsExactlyInAnyOrder("MINI-001", "MINI-002", "MINI-003");
    }

    /** Every stop carries a real schedule, in order, with service never starting before arrival. */
    @Test
    void everyStopCarriesAScheduleTheEvaluatorComputed() {
        var plan = planImportedInstance(2);

        for (var route : plan.routes()) {
            assertThat(route.stops())
                    .isSortedAccordingTo(Comparator.comparingInt(PlannedStopResponse::visitOrder));

            Instant previousDeparture = null;
            for (var stop : route.stops()) {
                assertThat(stop.serviceStart()).isAfterOrEqualTo(stop.arrival());
                assertThat(stop.departure()).isAfterOrEqualTo(stop.serviceStart());
                assertThat(stop.waitMinutes()).isNotNegative();
                if (previousDeparture != null) {
                    assertThat(stop.arrival()).isAfterOrEqualTo(previousDeparture);
                }
                previousDeparture = stop.departure();
            }
        }
    }

    /** Customer 1 is ready at minute 161 of 2026-01-05 UTC, which is 02:41. */
    @Test
    void scheduleTimesAreRealInstantsOnTheServiceDate() {
        var plan = planImportedInstance(2);

        var first = plan.routes().stream()
                .flatMap(r -> r.stops().stream())
                .filter(s -> s.reference().equals("MINI-001"))
                .findFirst()
                .orElseThrow();

        assertThat(first.serviceStart()).isAfterOrEqualTo(Instant.parse("2026-01-05T02:41:00Z"));
        assertThat(first.serviceStart()).isBeforeOrEqualTo(Instant.parse("2026-01-05T02:51:00Z"));
    }

    @Test
    void theSameRunRendersAsSvg() {
        var imported = importer.importFrom(fixture(), SERVICE_DATE);

        var svg = rest.post()
                .uri("/api/plan-runs/svg")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PlanRunRequest(imported.depotId(), SERVICE_DATE, 2, null))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(svg).startsWith("<svg").contains("<polyline").endsWith("</svg>\n");
    }

    @Test
    void anUnknownDepotIsANotFound() {
        rest.post()
                .uri("/api/plan-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PlanRunRequest(999_999L, SERVICE_DATE, 1, null))
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void aRequestWithoutADepotIsABadRequest() {
        rest.post()
                .uri("/api/plan-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PlanRunRequest(null, SERVICE_DATE, 1, null))
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    /** Same seed, same instance, same answer — the determinism guarantee, over HTTP. */
    @Test
    void repeatingTheRequestReturnsTheSamePlan() {
        var imported = importer.importFrom(fixture(), SERVICE_DATE);
        var request = new PlanRunRequest(imported.depotId(), SERVICE_DATE, 2, 42L);

        var first = post(request);
        var second = post(request);

        assertThat(second.totalDistance()).isEqualTo(first.totalDistance());
        assertThat(second.vehiclesUsed()).isEqualTo(first.vehiclesUsed());
        assertThat(second.routes()).extracting(PlannedRouteResponse::stops).isEqualTo(
                first.routes().stream().map(PlannedRouteResponse::stops).toList());
        assertThat(second.seed()).isEqualTo(42L);
    }

    private PlanRunResponse post(PlanRunRequest request) {
        return rest.post()
                .uri("/api/plan-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PlanRunResponse.class)
                .returnResult()
                .getResponseBody();
    }
}
