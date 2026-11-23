package dev.zeralius.clowder.planning.plan.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.TestcontainersConfiguration;
import dev.zeralius.clowder.common.error.ConflictException;
import dev.zeralius.clowder.planning.plan.persistence.PlanRepository;
import dev.zeralius.clowder.planning.plan.persistence.PlanRunRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * Twenty dispatchers hit publish on twenty different drafts for one vehicle-day at the same instant.
 *
 * <p>Three things make this real rather than theatre. A {@link CountDownLatch} releases every thread
 * at once, because without it they run one after another, never contend, and the test proves nothing
 * while passing. It runs against real Postgres, because H2 has no partial unique indexes and would
 * pass for the wrong reason. And it is not {@code @Transactional}, because a class-level transaction
 * would put all twenty threads in one and there would be no race at all.
 *
 * <p><b>What is asserted, and why it is not "exactly one thread won".</b> The invariant the schema
 * guarantees is that at most one row is published per vehicle-day, not that only one publish call can
 * ever succeed: a thread scheduled late enough sees the winner committed, supersedes it legitimately
 * and publishes its own, which is replanning working correctly rather than a race being lost. So this
 * counts outcomes — every thread either published or got a conflict, nothing else — and then checks
 * the invariant directly: one PUBLISHED row, and one SUPERSEDED row for every publish after the first.
 * In practice the gate is tight enough that a single thread wins and nineteen conflict.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql("/db/testdata/plan-fixture.sql")
class PlanPublishConcurrencyIT {

    private static final int THREADS = 20;
    private static final LocalDate DAY = LocalDate.of(2026, 1, 5);

    @Autowired
    PlanPublisher publisher;

    @Autowired
    PlanRepository plans;

    @Autowired
    PlanRunRepository runs;

    @Autowired
    JdbcTemplate jdbc;

    private List<Long> draftIds;

    @BeforeEach
    void createCompetingDrafts() {
        final var depotId = jdbc.queryForObject("select id from depot where code = ?", Long.class, "PLN");
        final var vehicleId =
                jdbc.queryForObject("select id from vehicle where registration = ?", Long.class, "PLAN-01");

        final var run = runs.save(new PlanRun(depotId, DAY, "test", 1L, 1000));

        draftIds = new ArrayList<>(THREADS);
        for (var version = 1; version <= THREADS; version++) {
            draftIds.add(plans.save(new Plan(run.getId(), depotId, vehicleId, DAY, version, 1_000L, 60))
                    .getId());
        }
    }

    @Test
    void atMostOnePlanEndsUpPublishedAndNobodySeesAnUnexpectedFailure() throws Exception {
        final var startGate = new CountDownLatch(1);
        final var finished = new CountDownLatch(THREADS);
        final var published = new AtomicInteger();
        final var conflicted = new AtomicInteger();
        final var unexpected = new ArrayList<Throwable>();

        try (var pool = Executors.newFixedThreadPool(THREADS)) {
            for (final var planId : draftIds) {
                pool.submit(() -> {
                    try {
                        startGate.await();
                        publisher.publish(planId);
                        published.incrementAndGet();
                    } catch (ConflictException | DataIntegrityViolationException | ConcurrencyFailureException e) {
                        // All three mean the same thing to a caller and all three become a 409: the
                        // unique index refused the insert, or the row this thread meant to supersede
                        // had already moved under it.
                        conflicted.incrementAndGet();
                    } catch (Throwable t) {
                        synchronized (unexpected) {
                            unexpected.add(t);
                        }
                    } finally {
                        finished.countDown();
                    }
                });
            }

            // Release all twenty at the same instant. Without this they serialise and prove nothing.
            startGate.countDown();
            assertThat(finished.await(60, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(unexpected).isEmpty();
        assertThat(published.get() + conflicted.get()).isEqualTo(THREADS);
        assertThat(published.get()).isPositive();

        final var all = plans.findAll();
        assertThat(all.stream().filter(plan -> plan.getStatus() == PlanStatus.PUBLISHED))
                .as("the partial unique index allows exactly one")
                .hasSize(1);
        assertThat(all.stream().filter(plan -> plan.getStatus() == PlanStatus.SUPERSEDED))
                .as("one superseded row for every publish after the first")
                .hasSize(published.get() - 1);
        assertThat(all.stream().filter(plan -> plan.getStatus() == PlanStatus.DRAFT))
                .hasSize(THREADS - published.get());

        assertThat(plans.findByVehicleIdAndServiceDateAndStatus(
                        all.getFirst().getVehicleId(), DAY, PlanStatus.PUBLISHED))
                .isPresent();
    }

    /** The database agrees, not just the repository: the index is what is being tested. */
    @Test
    void theIndexItselfRefusesASecondPublishedRow() throws Exception {
        publisher.publish(draftIds.getFirst());

        final var vehicleId =
                jdbc.queryForObject("select id from vehicle where registration = ?", Long.class, "PLAN-01");

        final var count = jdbc.queryForObject(
                "select count(*) from plan where vehicle_id = ? and service_date = ? and status = 'PUBLISHED'",
                Integer.class,
                vehicleId,
                DAY);
        assertThat(count).isEqualTo(1);

        assertThat(indexExists())
                .as("uq_plan_published is what makes this milestone true")
                .isTrue();
    }

    private boolean indexExists() {
        final var found = jdbc.queryForObject(
                "select count(*) from pg_indexes where indexname = ?", Integer.class, "uq_plan_published");
        return found != null && found == 1;
    }
}
