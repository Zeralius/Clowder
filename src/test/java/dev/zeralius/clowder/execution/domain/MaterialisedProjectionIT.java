package dev.zeralius.clowder.execution.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zeralius.clowder.TestcontainersConfiguration;
import dev.zeralius.clowder.execution.PublishedPlanFixture;
import dev.zeralius.clowder.execution.api.RecordEventRequest;
import dev.zeralius.clowder.execution.persistence.DriverEventRepository;
import dev.zeralius.clowder.execution.persistence.PlanExecutionStateRepository;
import dev.zeralius.clowder.execution.persistence.PlanStopStateRepository;
import dev.zeralius.clowder.planning.plan.api.PlanResponse;
import dev.zeralius.clowder.security.TestAuth;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

/**
 * The materialised projection is a cache, and the only thing that makes a cache acceptable here is
 * that it can be shown to equal the thing it caches. So that is what this checks, from both ends: the
 * stored rows against a fresh fold, and a deliberately corrupted cache against a rebuild.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, PublishedPlanFixture.class})
@Sql("/db/testdata/depots.sql")
class MaterialisedProjectionIT {

    @Autowired
    EventIngestionService ingestion;

    @Autowired
    ExecutionStateUpdater updater;

    @Autowired
    DivergenceService divergence;

    @Autowired
    DriverEventRepository events;

    @Autowired
    PlanExecutionStateRepository planState;

    @Autowired
    PlanStopStateRepository stopState;

    @Autowired
    PublishedPlanFixture fixture;

    @AfterEach
    void clearTheCaller() {
        TestAuth.clear();
    }

    private final PlanProjector projector = new PlanProjector();

    private PlanResponse plan;

    @BeforeEach
    void publishAPlan() {
        // A dispatcher: these tests are about the mechanics, not about who may.
        TestAuth.asDispatcher();
        plan = fixture.publishedPlan();
    }

    /**
     * Called by the tests that need a part-driven route. Not in {@code @BeforeEach}, because one test
     * is about a plan nobody has reported against and the fixture cannot be built twice in one method
     * — it imports the same instance, and the vehicle registrations are unique.
     */
    private void runPartOfTheRoute() {
        event("depart", null, DriverEventType.DEPARTED_DEPOT, -30, null);
        event("arrive-1", 0, DriverEventType.ARRIVED, -5, null);
        event("deliver-1", 0, DriverEventType.DELIVERED, 0, null);
        event("fail-2", 1, DriverEventType.FAILED, 50, FailureReason.REFUSED);
    }

    private void event(
            String key, Integer stopIndex, DriverEventType type, int minutesFromFirst, FailureReason reason) {
        ingestion.record(
                plan.id(),
                new RecordEventRequest(
                        key,
                        stopIndex == null ? null : plan.stops().get(stopIndex).id(),
                        type,
                        plan.stops().getFirst().plannedDeparture().plus(Duration.ofMinutes(minutesFromFirst)),
                        null,
                        null,
                        reason));
    }

    private PlanProjection folded() {
        return projector.project(plan.id(), plan.stops(), events.findByPlanIdOrderByOccurredAtAscIdAsc(plan.id()));
    }

    @Test
    void ingestingAnEventWritesTheProjection() {
        runPartOfTheRoute();

        assertThat(planState.findById(plan.id())).isPresent();
        assertThat(stopState.findByPlanIdOrderByPlanStopIdAsc(plan.id())).hasSize(3);
    }

    @Test
    void theStoredHeaderSaysHowMuchOfTheLogItCameFrom() {
        runPartOfTheRoute();

        final var header = planState.findById(plan.id()).orElseThrow();

        assertThat(header.getEventsApplied()).isEqualTo((int) events.countByPlanId(plan.id()));
        assertThat(header.getUpdatedAt()).isNotNull();
    }

    /** The claim the whole thing rests on. */
    @Test
    void theStoredProjectionEqualsAFreshFoldOfTheLog() {
        runPartOfTheRoute();

        final var stored = divergence.track(plan.id(), plan.stops().getFirst().plannedDeparture());
        final var fold = folded();

        assertThat(stored.stopsCompleted()).isEqualTo(fold.completed());
        assertThat(stored.stopsRemaining()).isEqualTo(fold.remaining());
        assertThat(stored.currentDelayMinutes()).isEqualTo(fold.currentDelayMinutes());
        assertThat(stored.lastKnownDeparture()).isEqualTo(fold.lastKnownDeparture());
        assertThat(stored.stops()).isEqualTo(fold.stops());
    }

    @Test
    void rebuildingFromTheLogReproducesTheSameRows() {
        runPartOfTheRoute();

        final var before = divergence.track(plan.id(), plan.stops().getFirst().plannedDeparture());

        updater.rebuild(plan.id());

        final var after = divergence.track(plan.id(), plan.stops().getFirst().plannedDeparture());

        assertThat(after.stops()).isEqualTo(before.stops());
        assertThat(after.stopsCompleted()).isEqualTo(before.stopsCompleted());
        assertThat(after.currentDelayMinutes()).isEqualTo(before.currentDelayMinutes());
    }

    /** Corrupt the cache on purpose, rebuild, and the log wins. */
    @Test
    void aRebuildRepairsACacheThatHasBeenTamperedWith() {
        runPartOfTheRoute();

        final var correct = divergence.track(plan.id(), plan.stops().getFirst().plannedDeparture());

        stopState.deleteByPlanId(plan.id());
        stopState.flush();

        updater.rebuild(plan.id());

        assertThat(divergence
                        .track(plan.id(), plan.stops().getFirst().plannedDeparture())
                        .stops())
                .isEqualTo(correct.stops());
    }

    /**
     * With the stop rows gone and no rebuild, tracking falls back to folding rather than reporting a
     * half-empty route. The cache is an optimisation, and this is what keeps it only that.
     */
    @Test
    void trackingFallsBackToTheFoldWhenTheCacheIsIncomplete() {
        runPartOfTheRoute();

        final var withCache =
                divergence.track(plan.id(), plan.stops().getFirst().plannedDeparture());

        stopState.deleteByPlanId(plan.id());
        stopState.flush();

        final var withoutCache =
                divergence.track(plan.id(), plan.stops().getFirst().plannedDeparture());

        assertThat(withoutCache.stops()).isEqualTo(withCache.stops());
        assertThat(withoutCache.stopsCompleted()).isEqualTo(withCache.stopsCompleted());
    }

    /** A plan nobody has reported against has no stored rows, and tracking still answers. */
    @Test
    void aPlanWithNoEventsHasNothingStoredAndStillTracks() {
        assertThat(planState.findById(plan.id())).isEmpty();
        assertThat(stopState.findByPlanIdOrderByPlanStopIdAsc(plan.id())).isEmpty();

        final var tracking = divergence.track(plan.id(), plan.stops().getFirst().plannedDeparture());

        assertThat(tracking.stopsRemaining()).isEqualTo(plan.stops().size());
        assertThat(tracking.stops()).allMatch(stop -> stop.status() == StopStatus.PENDING);
    }

    /** The refresh writes in the caller transaction, so calling it outside one is a programming error. */
    @Test
    void refreshingOutsideATransactionIsRefused() {
        runPartOfTheRoute();

        assertThatThrownBy(() -> updater.refresh(plan.id(), plan.stops()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inside the transaction");
    }
}
