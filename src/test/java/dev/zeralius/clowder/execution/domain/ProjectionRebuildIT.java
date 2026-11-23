package dev.zeralius.clowder.execution.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.TestcontainersConfiguration;
import dev.zeralius.clowder.execution.PublishedPlanFixture;
import dev.zeralius.clowder.execution.api.RecordEventRequest;
import dev.zeralius.clowder.execution.persistence.DriverEventRepository;
import dev.zeralius.clowder.planning.plan.api.PlanResponse;
import dev.zeralius.clowder.security.TestAuth;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

/**
 * Worth more than the feature it tests. If state can be rebuilt from the raw log and comes out
 * identical, then deriving it instead of storing it is not a stylistic preference — it is the only
 * copy, and there is no cache to drift.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, PublishedPlanFixture.class})
@Sql("/db/testdata/depots.sql")
class ProjectionRebuildIT {

    @Autowired
    EventIngestionService ingestion;

    @Autowired
    DriverEventRepository events;

    @Autowired
    PublishedPlanFixture fixture;

    @AfterEach
    void clearTheCaller() {
        TestAuth.clear();
    }

    private final PlanProjector projector = new PlanProjector();

    private PlanResponse plan;

    @BeforeEach
    void publishAPlanAndRunMostOfIt() {
        // A dispatcher: these tests are about the mechanics, not about who may.
        TestAuth.asDispatcher();
        plan = fixture.publishedPlan();

        final var first = plan.stops().get(0);
        final var second = plan.stops().get(1);

        record("depart", null, DriverEventType.DEPARTED_DEPOT, -30, null);
        record("arrive-1", first.id(), DriverEventType.ARRIVED, -5, null);
        record("deliver-1", first.id(), DriverEventType.DELIVERED, 0, null);
        record("arrive-2", second.id(), DriverEventType.ARRIVED, 40, null);
        record("fail-2", second.id(), DriverEventType.FAILED, 50, FailureReason.REFUSED);
    }

    private void record(
            String key, Long stopId, DriverEventType type, int minutesFromFirstDeparture, FailureReason reason) {
        ingestion.record(
                plan.id(),
                new RecordEventRequest(
                        key,
                        stopId,
                        type,
                        plan.stops().getFirst().plannedDeparture().plus(Duration.ofMinutes(minutesFromFirstDeparture)),
                        null,
                        null,
                        reason));
    }

    private List<DriverEvent> log() {
        return events.findByPlanIdOrderByOccurredAtAscIdAsc(plan.id());
    }

    @Test
    void rebuildingFromTheRawLogInAnyOrderGivesTheSameAnswer() {
        final var live = projector.project(plan.id(), plan.stops(), log());

        // The projector sorts by device time, so handing it the same events in a different order must
        // not move the result. If it ever starts depending on arrival order, this fails at once.
        final var shuffled = new ArrayList<>(log());
        Collections.shuffle(shuffled, new Random(20260105L));

        assertThat(projector.project(plan.id(), plan.stops(), shuffled)).isEqualTo(live);
    }

    @Test
    void theRebuiltStateIsTheStateTheLogDescribes() {
        final var projection = projector.project(plan.id(), plan.stops(), log());

        assertThat(projection.completed()).isEqualTo(2);
        assertThat(projection.remaining()).isEqualTo(1);
        assertThat(projection.stops())
                .extracting(StopState::status)
                .containsExactly(StopStatus.DELIVERED, StopStatus.FAILED, StopStatus.PENDING);
        assertThat(projection.stops().get(1).failureReason()).isEqualTo(FailureReason.REFUSED);
    }

    @Test
    void droppingTheLogLeavesEveryStopPendingAgain() {
        final var projection = projector.project(plan.id(), plan.stops(), List.of());

        assertThat(projection.completed()).isZero();
        assertThat(projection.remaining()).isEqualTo(plan.stops().size());
        assertThat(projection.stops()).allMatch(stop -> stop.status() == StopStatus.PENDING);
    }

    /** Nothing in the log is ever rewritten, so a replay cannot change what the projection says. */
    @Test
    void replayingEveryEventChangesNothing() {
        final var before = projector.project(plan.id(), plan.stops(), log());
        final var countBefore = events.countByPlanId(plan.id());

        publishAPlanAndRunMostOfItAgain();

        assertThat(events.countByPlanId(plan.id())).isEqualTo(countBefore);
        assertThat(projector.project(plan.id(), plan.stops(), log())).isEqualTo(before);
    }

    private void publishAPlanAndRunMostOfItAgain() {
        final var first = plan.stops().get(0);
        final var second = plan.stops().get(1);

        record("depart", null, DriverEventType.DEPARTED_DEPOT, -30, null);
        record("arrive-1", first.id(), DriverEventType.ARRIVED, -5, null);
        record("deliver-1", first.id(), DriverEventType.DELIVERED, 0, null);
        record("arrive-2", second.id(), DriverEventType.ARRIVED, 40, null);
        record("fail-2", second.id(), DriverEventType.FAILED, 50, FailureReason.REFUSED);
    }
}
