package dev.zeralius.clowder.execution.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.TestcontainersConfiguration;
import dev.zeralius.clowder.execution.PublishedPlanFixture;
import dev.zeralius.clowder.execution.api.ProjectedMiss;
import dev.zeralius.clowder.execution.api.RecordEventRequest;
import dev.zeralius.clowder.planning.plan.api.PlanResponse;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

/**
 * Plan against reality. The clock is a parameter rather than the system time, which is the only reason
 * these assertions can be exact instead of approximate.
 *
 * <p>MINI gives one vehicle three stops with tight windows, so a single late delivery is enough to put
 * the rest of the route out of reach — which is precisely the report a dispatcher needs at 11am.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, PublishedPlanFixture.class})
@Sql("/db/testdata/depots.sql")
class DivergenceIT {

    @Autowired
    DivergenceService divergence;

    @Autowired
    EventIngestionService ingestion;

    @Autowired
    PublishedPlanFixture fixture;

    private PlanResponse plan;

    @BeforeEach
    void publishAPlan() {
        plan = fixture.publishedPlan();
        assertThat(plan.stops()).hasSize(3);
    }

    private void deliver(String key, int stopIndex, Instant at) {
        ingestion.record(
                plan.id(),
                new RecordEventRequest(
                        key,
                        plan.stops().get(stopIndex).id(),
                        DriverEventType.DELIVERED,
                        at,
                        null,
                        null,
                        null));
    }

    private Instant plannedDeparture(int stopIndex) {
        return plan.stops().get(stopIndex).plannedDeparture();
    }

    @Test
    void anUntouchedPlanHasEverythingAheadOfItAndNoDelay() {
        final var tracking = divergence.track(plan.id(), plannedDeparture(0).minus(Duration.ofHours(1)));

        assertThat(tracking.stopsCompleted()).isZero();
        assertThat(tracking.stopsRemaining()).isEqualTo(3);
        assertThat(tracking.currentDelayMinutes()).isZero();
        assertThat(tracking.lastKnownDeparture()).isNull();
        assertThat(tracking.stops()).allMatch(stop -> stop.status() == StopStatus.PENDING);
    }

    /** Delivering the first stop exactly on plan leaves the rest of the route reachable. */
    @Test
    void aRouteRunningToPlanProjectsNoMisses() {
        final var onTime = plannedDeparture(0);
        deliver("k1", 0, onTime);

        final var tracking = divergence.track(plan.id(), onTime);

        assertThat(tracking.stopsCompleted()).isEqualTo(1);
        assertThat(tracking.stopsRemaining()).isEqualTo(2);
        assertThat(tracking.currentDelayMinutes()).isZero();
        assertThat(tracking.projectedWindowMisses()).isEmpty();
    }

    /** An hour late at the first stop, and the two tight windows after it are gone. */
    @Test
    void anHourLateAtTheFirstStopPutsTheRestOutOfReach() {
        final var late = plannedDeparture(0).plus(Duration.ofHours(1));
        deliver("k1", 0, late);

        final var tracking = divergence.track(plan.id(), late);

        assertThat(tracking.currentDelayMinutes()).isEqualTo(60);
        assertThat(tracking.projectedWindowMisses()).hasSize(2);

        assertThat(tracking.projectedWindowMisses())
                .extracting(ProjectedMiss::consignmentId)
                .containsExactlyInAnyOrderElementsOf(plan.stops().stream()
                        .skip(1)
                        .map(stop -> stop.consignmentId())
                        .toList());

        assertThat(tracking.projectedWindowMisses()).allSatisfy(miss -> {
            assertThat(miss.reference()).isNotBlank();
            assertThat(miss.lateByMinutes()).isPositive();
            assertThat(miss.projectedArrival()).isAfter(miss.windowEnd());
        });
    }

    /** Running early is reported with the other sign, not clamped to zero. */
    @Test
    void anEarlyDeliveryIsANegativeDelay() {
        final var early = plannedDeparture(0).minus(Duration.ofMinutes(12));
        deliver("k1", 0, early);

        assertThat(divergence.track(plan.id(), early).currentDelayMinutes()).isEqualTo(-12);
    }

    @Test
    void aFailedStopCountsAsCompletedAndCarriesItsReason() {
        ingestion.record(
                plan.id(),
                new RecordEventRequest(
                        "k1",
                        plan.stops().getFirst().id(),
                        DriverEventType.FAILED,
                        plannedDeparture(0),
                        null,
                        null,
                        FailureReason.CUSTOMER_ABSENT));

        final var tracking = divergence.track(plan.id(), plannedDeparture(0));

        assertThat(tracking.stopsCompleted()).isEqualTo(1);
        assertThat(tracking.stops().getFirst().status()).isEqualTo(StopStatus.FAILED);
        assertThat(tracking.stops().getFirst().failureReason()).isEqualTo(FailureReason.CUSTOMER_ABSENT);
    }

    /** With nothing left there is nothing to project, however late the day ran. */
    @Test
    void aFinishedRouteProjectsNoMisses() {
        for (var i = 0; i < 3; i++) {
            deliver("k" + i, i, plannedDeparture(i).plus(Duration.ofHours(2)));
        }

        final var tracking = divergence.track(plan.id(), plannedDeparture(2).plus(Duration.ofHours(3)));

        assertThat(tracking.stopsCompleted()).isEqualTo(3);
        assertThat(tracking.stopsRemaining()).isZero();
        assertThat(tracking.projectedWindowMisses()).isEmpty();
    }

    /** Events in the wrong order fold to the same state, through the real log rather than a list. */
    @Test
    void anOutOfOrderBurstFoldsToTheSameState() {
        ingestion.record(
                plan.id(),
                new RecordEventRequest(
                        "later",
                        plan.stops().getFirst().id(),
                        DriverEventType.DELIVERED,
                        plannedDeparture(0),
                        null,
                        null,
                        null));
        ingestion.record(
                plan.id(),
                new RecordEventRequest(
                        "earlier",
                        plan.stops().getFirst().id(),
                        DriverEventType.ARRIVED,
                        plannedDeparture(0).minus(Duration.ofMinutes(10)),
                        null,
                        null,
                        null));

        final var tracking = divergence.track(plan.id(), plannedDeparture(0));
        final var first = tracking.stops().getFirst();

        assertThat(first.status()).isEqualTo(StopStatus.DELIVERED);
        assertThat(first.actualArrival()).isEqualTo(plannedDeparture(0).minus(Duration.ofMinutes(10)));
    }
}
