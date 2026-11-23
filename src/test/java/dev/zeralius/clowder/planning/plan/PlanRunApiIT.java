package dev.zeralius.clowder.planning.plan;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.TestcontainersConfiguration;
import dev.zeralius.clowder.importer.SolomonImportService;
import dev.zeralius.clowder.planning.plan.api.PlanResponse;
import dev.zeralius.clowder.planning.plan.api.PlanRunRequest;
import dev.zeralius.clowder.planning.plan.api.PlanRunResponse;
import dev.zeralius.clowder.planning.plan.api.PlanStopResponse;
import dev.zeralius.clowder.planning.plan.domain.PlanRunStatus;
import dev.zeralius.clowder.planning.plan.domain.PlanStatus;
import dev.zeralius.clowder.security.TestTokens;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The end-to-end path this project exists for: data in through the import API, a stored, versioned,
 * publishable plan out through the planning API.
 *
 * <p>Not {@code @Transactional}: the import must commit before the server request thread can see it,
 * the same reason {@code DepotApiIT} cannot wrap its requests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TestTokens.class})
@Sql("/db/testdata/plan-fixture.sql")
class PlanRunApiIT {

    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 1, 5);

    @Autowired
    SolomonImportService importer;

    @LocalServerPort
    int port;

    private RestTestClient rest;

    @Autowired
    TestTokens tokens;

    /** A dispatcher: planning and publishing are dispatcher work, and that is what these exercise. */
    @BeforeEach
    void bindToTheRunningServer() {
        rest = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .defaultHeader(HttpHeaders.AUTHORIZATION, tokens.bearerForDispatcher())
                .build();
    }

    private static Reader fixture() {
        final var stream =
                Objects.requireNonNull(PlanRunApiIT.class.getResourceAsStream("/solomon/MINI.txt"), "fixture missing");
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }

    /** Imports the instance and solves it, returning the stored run. */
    private PlanRunResponse run(Long seed) {
        final var imported = importer.importFrom(fixture(), SERVICE_DATE);
        return post(new PlanRunRequest(imported.depotId(), SERVICE_DATE, 2, seed));
    }

    /** The accepted response names the run and nothing has to have happened yet. */
    @Test
    void acceptingARunReturnsItBeforeTheSolveHasFinished() {
        final var imported = importer.importFrom(fixture(), SERVICE_DATE);

        final var accepted = accept(new PlanRunRequest(imported.depotId(), SERVICE_DATE, 2, null));

        assertThat(accepted.id()).isNotNull();
        assertThat(accepted.status()).isIn(PlanRunStatus.RUNNING, PlanRunStatus.SUCCEEDED);
        assertThat(accepted.startedAt()).isNotNull();

        assertThat(awaitRun(accepted.id()).status()).isEqualTo(PlanRunStatus.SUCCEEDED);
    }

    /** Posts the run and follows it to completion, which is what a real client does with the 202. */
    private PlanRunResponse post(PlanRunRequest request) {
        return awaitRun(accept(request).id());
    }

    private PlanRunResponse accept(PlanRunRequest request) {
        return rest.post()
                .uri("/api/plan-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus()
                .isAccepted()
                .expectHeader()
                .exists(HttpHeaders.LOCATION)
                .expectBody(PlanRunResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private PlanRunResponse getRun(Long id) {
        return rest.get()
                .uri("/api/plan-runs/" + id)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PlanRunResponse.class)
                .returnResult()
                .getResponseBody();
    }

    /**
     * The solve happens on a worker, so the run is usually RUNNING when the 202 comes back and the
     * caller polls. This is that poll, and it is the only thing the move to a worker changed about
     * how a client uses the resource.
     */
    private PlanRunResponse awaitRun(Long id) {
        final var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            final var run = getRun(id);
            if (run.status() != PlanRunStatus.RUNNING) {
                return run;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("Plan run " + id + " was still RUNNING after 30s");
    }

    private PlanResponse plan(Long id) {
        return rest.get()
                .uri("/api/plans/" + id)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PlanResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private List<PlanResponse> plansOf(PlanRunResponse run) {
        return run.planIds().stream().map(this::plan).toList();
    }

    @Test
    void acceptsARunAndStoresADraftPlanPerVehicleUsed() {
        final var run = run(null);

        assertThat(run.id()).isNotNull();
        assertThat(run.status()).isEqualTo(PlanRunStatus.SUCCEEDED);
        assertThat(run.serviceDate()).isEqualTo(SERVICE_DATE);
        assertThat(run.solverVersion()).isNotBlank();
        assertThat(run.unassignedReferences()).isEmpty();
        assertThat(run.finishedAt()).isAfterOrEqualTo(run.startedAt());
        assertThat(run.elapsedMs()).isNotNull();
        assertThat(run.planIds()).isNotEmpty().hasSizeBetween(1, 3);

        assertThat(plansOf(run)).allSatisfy(plan -> {
            assertThat(plan.status()).isEqualTo(PlanStatus.DRAFT);
            assertThat(plan.versionNo()).isEqualTo(1);
            assertThat(plan.publishedAt()).isNull();
            assertThat(plan.publishedBy()).isNull();
            assertThat(plan.stops()).isNotEmpty();
            assertThat(plan.totalDistance()).isPositive();
        });
    }

    /** Every one of the three consignments is on exactly one plan, with its reference snapshotted. */
    @Test
    void everyConsignmentIsPlannedExactlyOnce() {
        final var references = plansOf(run(null)).stream()
                .flatMap(plan -> plan.stops().stream())
                .map(PlanStopResponse::reference)
                .toList();

        assertThat(references).containsExactlyInAnyOrder("MINI-001", "MINI-002", "MINI-003");
    }

    @Test
    void theRunIsReadableBackAtItsOwnUrl() {
        final var created = run(null);

        final var fetched = getRun(created.id());

        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.planIds()).isEqualTo(created.planIds());
        assertThat(fetched.seed()).isEqualTo(created.seed());
    }

    /** Every stop carries a real schedule, in order, with service never starting before arrival. */
    @Test
    void everyStopCarriesAScheduleTheEvaluatorComputed() {
        for (final var plan : plansOf(run(null))) {
            assertThat(plan.stops()).isSortedAccordingTo(Comparator.comparingInt(PlanStopResponse::sequenceNo));

            Instant previousDeparture = null;
            for (final var stop : plan.stops()) {
                assertThat(stop.plannedServiceStart()).isAfterOrEqualTo(stop.plannedArrival());
                assertThat(stop.plannedDeparture()).isAfterOrEqualTo(stop.plannedServiceStart());
                assertThat(stop.waitMinutes()).isNotNegative();
                if (previousDeparture != null) {
                    assertThat(stop.plannedArrival()).isAfterOrEqualTo(previousDeparture);
                }
                previousDeparture = stop.plannedDeparture();
            }
        }
    }

    /** Customer 1 is ready at minute 161 of 2026-01-05 UTC, which is 02:41. */
    @Test
    void scheduleTimesAreRealInstantsOnTheServiceDate() {
        final var first = plansOf(run(null)).stream()
                .flatMap(plan -> plan.stops().stream())
                .filter(stop -> stop.reference().equals("MINI-001"))
                .findFirst()
                .orElseThrow();

        assertThat(first.plannedServiceStart()).isAfterOrEqualTo(Instant.parse("2026-01-05T02:41:00Z"));
        assertThat(first.plannedServiceStart()).isBeforeOrEqualTo(Instant.parse("2026-01-05T02:51:00Z"));
    }

    @Test
    void publishingADraftMakesItThePublishedPlanForThatVehicleDay() {
        final var planId = run(null).planIds().getFirst();

        final var published = rest.post()
                .uri("/api/plans/" + planId + "/publish")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PlanResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(published.status()).isEqualTo(PlanStatus.PUBLISHED);
        assertThat(published.publishedAt()).isNotNull();
        // The name comes off the bearer token this client sends, not from anything in the request:
        // there is no field a caller could publish under another name with.
        assertThat(published.publishedBy()).isEqualTo("test-dispatcher");
        assertThat(plan(planId).status()).isEqualTo(PlanStatus.PUBLISHED);
    }

    /** Replanning the same day produces v2 and supersedes v1 rather than editing it. */
    @Test
    void replanningProducesASecondVersionThatSupersedesTheFirst() {
        final var first = run(null);
        final var firstPlanId = first.planIds().getFirst();
        publish(firstPlanId);

        final var vehicleId = plan(firstPlanId).vehicleId();

        final var second = post(new PlanRunRequest(first.depotId(), SERVICE_DATE, 2, null));
        final var secondPlanId = plansOf(second).stream()
                .filter(plan -> plan.vehicleId().equals(vehicleId))
                .map(PlanResponse::id)
                .findFirst()
                .orElseThrow();

        assertThat(plan(secondPlanId).versionNo()).isEqualTo(2);

        publish(secondPlanId);

        assertThat(plan(firstPlanId).status()).isEqualTo(PlanStatus.SUPERSEDED);
        assertThat(plan(secondPlanId).status()).isEqualTo(PlanStatus.PUBLISHED);

        final var versions = rest.get()
                .uri("/api/plans?vehicleId=" + vehicleId + "&serviceDate=" + SERVICE_DATE)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PlanResponse[].class)
                .returnResult()
                .getResponseBody();

        assertThat(versions).extracting(PlanResponse::versionNo).containsExactly(2, 1);
    }

    /** Publishing a superseded plan again is a 409, not a silent revival. */
    @Test
    void republishingASupersededPlanIsAConflict() {
        final var first = run(null);
        final var firstPlanId = first.planIds().getFirst();
        publish(firstPlanId);

        final var vehicleId = plan(firstPlanId).vehicleId();
        final var second = post(new PlanRunRequest(first.depotId(), SERVICE_DATE, 2, null));
        final var secondPlanId = plansOf(second).stream()
                .filter(plan -> plan.vehicleId().equals(vehicleId))
                .map(PlanResponse::id)
                .findFirst()
                .orElseThrow();
        publish(secondPlanId);

        rest.post()
                .uri("/api/plans/" + firstPlanId + "/publish")
                .exchange()
                .expectStatus()
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    private void publish(Long planId) {
        rest.post()
                .uri("/api/plans/" + planId + "/publish")
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void theSameRunRendersAsSvg() {
        final var imported = importer.importFrom(fixture(), SERVICE_DATE);

        final var svg = rest.post()
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

    /** A bad depot is a bad request, not an accepted run that reports FAILED a moment later. */
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

    @Test
    void anUnknownPlanIsANotFound() {
        rest.get().uri("/api/plans/999999").exchange().expectStatus().isNotFound();
    }

    @Test
    void anUnknownRunIsANotFound() {
        rest.get().uri("/api/plan-runs/999999").exchange().expectStatus().isNotFound();
    }

    /** Same seed, same instance, same answer — the determinism guarantee, now over stored plans. */
    @Test
    void repeatingTheRequestStoresTheSamePlan() {
        final var imported = importer.importFrom(fixture(), SERVICE_DATE);
        final var request = new PlanRunRequest(imported.depotId(), SERVICE_DATE, 2, 42L);

        final var first = post(request);
        final var second = post(request);

        assertThat(second.seed()).isEqualTo(42L);

        final var firstStops = stopsOf(first);
        assertThat(stopsOf(second)).isEqualTo(firstStops);
        assertThat(totalDistance(second)).isEqualTo(totalDistance(first));

        // Same vehicle-day, so the second run is version 2 of each plan.
        assertThat(plansOf(second))
                .allSatisfy(plan -> assertThat(plan.versionNo()).isEqualTo(2));
    }

    private List<List<Long>> stopsOf(PlanRunResponse run) {
        return plansOf(run).stream()
                .map(plan -> plan.stops().stream()
                        .map(PlanStopResponse::consignmentId)
                        .toList())
                .toList();
    }

    private long totalDistance(PlanRunResponse run) {
        return plansOf(run).stream().mapToLong(PlanResponse::totalDistance).sum();
    }
}
