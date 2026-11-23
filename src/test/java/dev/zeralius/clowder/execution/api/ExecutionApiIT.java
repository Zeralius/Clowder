package dev.zeralius.clowder.execution.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.TestcontainersConfiguration;
import dev.zeralius.clowder.execution.PublishedPlanFixture;
import dev.zeralius.clowder.execution.domain.DriverEventType;
import dev.zeralius.clowder.execution.domain.FailureReason;
import dev.zeralius.clowder.execution.domain.StopStatus;
import dev.zeralius.clowder.planning.plan.api.PlanResponse;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The driver side over HTTP: post what happened, read back how the day is going.
 *
 * <p>Not {@code @Transactional}: the fixture has to commit before the server request thread can see
 * it, the same reason the other API tests cannot wrap their requests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, PublishedPlanFixture.class})
@Sql("/db/testdata/depots.sql")
class ExecutionApiIT {

    @Autowired
    PublishedPlanFixture fixture;

    @LocalServerPort
    int port;

    private RestTestClient rest;
    private PlanResponse plan;

    @BeforeEach
    void publishAPlan() {
        rest = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        plan = fixture.publishedPlan();
    }

    private RecordEventRequest request(String key, Long stopId, DriverEventType type, Instant at) {
        return new RecordEventRequest(key, stopId, type, at, null, null, null);
    }

    private EventResponse post(RecordEventRequest request) {
        return rest.post()
                .uri("/api/plans/" + plan.id() + "/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(EventResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private TrackingResponse tracking() {
        return rest.get()
                .uri("/api/plans/" + plan.id() + "/tracking")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(TrackingResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private Instant plannedDeparture(int stopIndex) {
        return plan.stops().get(stopIndex).plannedDeparture();
    }

    @Test
    void postingAnEventIsAlwaysTwoHundredEvenOnARetry() {
        final var body = request("key-1", null, DriverEventType.DEPARTED_DEPOT, plannedDeparture(0));

        final var first = post(body);
        final var retry = post(body);

        assertThat(first.replayed()).isFalse();
        assertThat(retry.replayed()).isTrue();
        assertThat(retry.id()).isEqualTo(first.id());
    }

    @Test
    void theServerStampsItsOwnReceiptTime() {
        final var recorded = post(request("key-1", null, DriverEventType.DEPARTED_DEPOT, plannedDeparture(0)));

        assertThat(recorded.occurredAt()).isEqualTo(plannedDeparture(0));
        assertThat(recorded.receivedAt()).isNotNull().isNotEqualTo(recorded.occurredAt());
    }

    @Test
    void trackingReportsProgressAsEventsArrive() {
        assertThat(tracking().stopsRemaining()).isEqualTo(3);

        post(request("k1", plan.stops().getFirst().id(), DriverEventType.ARRIVED, plannedDeparture(0)));

        final var arrived = tracking();
        assertThat(arrived.stopsCompleted()).isZero();
        assertThat(arrived.stops().getFirst().status()).isEqualTo(StopStatus.ARRIVED);

        post(request("k2", plan.stops().getFirst().id(), DriverEventType.DELIVERED, plannedDeparture(0)));

        final var delivered = tracking();
        assertThat(delivered.stopsCompleted()).isEqualTo(1);
        assertThat(delivered.stopsRemaining()).isEqualTo(2);
        assertThat(delivered.stops().getFirst().status()).isEqualTo(StopStatus.DELIVERED);
    }

    /**
     * The service date is in the past, so by the real clock every remaining window has closed. That is
     * exactly the report a dispatcher should get, and it proves the endpoint runs the evaluator rather
     * than echoing the plan.
     */
    @Test
    void trackingProjectsMissesForWindowsThatCanNoLongerBeMet() {
        post(request("k1", plan.stops().getFirst().id(), DriverEventType.DELIVERED, plannedDeparture(0)));

        final var tracking = tracking();

        assertThat(tracking.projectedWindowMisses()).hasSize(2);
        assertThat(tracking.projectedWindowMisses()).allSatisfy(miss -> {
            assertThat(miss.reference()).startsWith("MINI-");
            assertThat(miss.lateByMinutes()).isPositive();
        });
    }

    @Test
    void aFailedDeliveryIsAnOrdinaryOutcomeWithAReason() {
        final var recorded = rest.post()
                .uri("/api/plans/" + plan.id() + "/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RecordEventRequest(
                        "k1",
                        plan.stops().getFirst().id(),
                        DriverEventType.FAILED,
                        plannedDeparture(0),
                        null,
                        null,
                        FailureReason.CUSTOMER_ABSENT))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(EventResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(recorded.failureReason()).isEqualTo(FailureReason.CUSTOMER_ABSENT);
        assertThat(tracking().stopsCompleted()).isEqualTo(1);
    }

    @Test
    void anEventWithoutAnIdempotencyKeyIsABadRequest() {
        rest.post()
                .uri("/api/plans/" + plan.id() + "/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request(null, null, DriverEventType.DEPARTED_DEPOT, plannedDeparture(0)))
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void anEventForAnUnknownPlanIsANotFound() {
        rest.post()
                .uri("/api/plans/999999/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request("k1", null, DriverEventType.DEPARTED_DEPOT, plannedDeparture(0)))
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void anEventNamingAStopOnAnotherPlanIsAConflict() {
        rest.post()
                .uri("/api/plans/" + plan.id() + "/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request("k1", 999_999L, DriverEventType.DELIVERED, plannedDeparture(0)))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void trackingAnUnknownPlanIsANotFound() {
        rest.get().uri("/api/plans/999999/tracking").exchange().expectStatus().isNotFound();
    }

    /** A burst upload after a tunnel: the same events, posted in the wrong order. */
    @Test
    void aBurstOfOutOfOrderEventsStillLandsOnTheRightState() {
        final var stopId = plan.stops().getFirst().id();

        post(request("later", stopId, DriverEventType.DELIVERED, plannedDeparture(0)));
        post(request(
                "earlier", stopId, DriverEventType.ARRIVED, plannedDeparture(0).minus(Duration.ofMinutes(8))));

        final var first = tracking().stops().getFirst();

        assertThat(first.status()).isEqualTo(StopStatus.DELIVERED);
        assertThat(first.actualArrival()).isEqualTo(plannedDeparture(0).minus(Duration.ofMinutes(8)));
    }
}
