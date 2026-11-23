package dev.zeralius.clowder.execution.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zeralius.clowder.TestcontainersConfiguration;
import dev.zeralius.clowder.common.error.ConflictException;
import dev.zeralius.clowder.common.error.NotFoundException;
import dev.zeralius.clowder.execution.PublishedPlanFixture;
import dev.zeralius.clowder.execution.api.RecordEventRequest;
import dev.zeralius.clowder.execution.persistence.DriverEventRepository;
import dev.zeralius.clowder.planning.plan.api.PlanResponse;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

/**
 * A driver on a bad connection retries until something answers. Every retry carries the same key, and
 * every retry has to get the same answer back.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, PublishedPlanFixture.class})
@Sql("/db/testdata/depots.sql")
class EventIdempotencyIT {

    private static final Instant OCCURRED = Instant.parse("2026-01-05T09:30:00Z");

    @Autowired
    EventIngestionService ingestion;

    @Autowired
    DriverEventRepository events;

    @Autowired
    PublishedPlanFixture fixture;

    private PlanResponse plan;

    @BeforeEach
    void publishAPlan() {
        plan = fixture.publishedPlan();
    }

    private RecordEventRequest departure(String key) {
        return new RecordEventRequest(key, null, DriverEventType.DEPARTED_DEPOT, OCCURRED, null, null, null);
    }

    @Test
    void replayingAKeyReturnsTheOriginalResponseAndInsertsNothing() {
        final var first = ingestion.record(plan.id(), departure("key-1"));
        final var second = ingestion.record(plan.id(), departure("key-1"));

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.receivedAt()).isEqualTo(first.receivedAt());
        assertThat(events.countByPlanId(plan.id())).isEqualTo(1);
    }

    /** A retry is a successful call, not a 409. The client cannot tell, and should not have to. */
    @Test
    void replayIsNotAConflict() {
        ingestion.record(plan.id(), departure("key-1"));

        assertThat(ingestion.record(plan.id(), departure("key-1")).replayed()).isTrue();
    }

    @Test
    void aDifferentKeyIsADifferentEvent() {
        ingestion.record(plan.id(), departure("key-1"));
        ingestion.record(plan.id(), departure("key-2"));

        assertThat(events.countByPlanId(plan.id())).isEqualTo(2);
    }

    /** Keys are unique across the whole log, so the same key under another plan is a client bug. */
    @Test
    void theSameKeyUnderADifferentPlanIsAConflict() {
        ingestion.record(plan.id(), departure("key-1"));

        assertThatThrownBy(() -> ingestion.record(plan.id() + 1, departure("key-1")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already belongs to plan");
    }

    @Test
    void anEventAgainstAnUnknownPlanIsNotFound() {
        assertThatThrownBy(() -> ingestion.record(999_999L, departure("key-1")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void aStopScopedEventNeedsAStopThatIsOnThePlan() {
        final var request = new RecordEventRequest(
                "key-1", 999_999L, DriverEventType.DELIVERED, OCCURRED, null, null, null);

        assertThatThrownBy(() -> ingestion.record(plan.id(), request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("is not on plan");
    }

    @Test
    void aStopScopedEventWithoutAStopIsAConflict() {
        final var request =
                new RecordEventRequest("key-1", null, DriverEventType.ARRIVED, OCCURRED, null, null, null);

        assertThatThrownBy(() -> ingestion.record(plan.id(), request)).isInstanceOf(ConflictException.class);
    }

    /** The server clock is recorded independently of the device clock, and is not the same number. */
    @Test
    void bothTimestampsAreStored() {
        final var recorded = ingestion.record(plan.id(), departure("key-1"));

        assertThat(recorded.occurredAt()).isEqualTo(OCCURRED);
        assertThat(recorded.receivedAt()).isNotNull().isAfter(OCCURRED);
    }

    /** Append-only: a stop-scoped event keeps its stop, and nothing in the log is ever updated. */
    @Test
    void aStopScopedEventIsStoredAgainstItsStop()  {
        final var stopId = plan.stops().getFirst().id();
        final var request = new RecordEventRequest(
                "key-1", stopId, DriverEventType.DELIVERED, OCCURRED, null, null, null);

        final var recorded = ingestion.record(plan.id(), request);

        assertThat(recorded.planStopId()).isEqualTo(stopId);
        assertThat(events.findByIdempotencyKey("key-1")).isPresent();
    }
}
