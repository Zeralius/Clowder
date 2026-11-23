package dev.zeralius.clowder.execution;

import dev.zeralius.clowder.importer.SolomonImportService;
import dev.zeralius.clowder.planning.plan.api.PlanResponse;
import dev.zeralius.clowder.planning.plan.api.PlanRunRequest;
import dev.zeralius.clowder.planning.plan.api.PlanRunResponse;
import dev.zeralius.clowder.planning.plan.domain.PlanPublisher;
import dev.zeralius.clowder.planning.plan.domain.PlanQueryService;
import dev.zeralius.clowder.planning.plan.domain.PlanRunService;
import dev.zeralius.clowder.planning.plan.domain.PlanRunStatus;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.boot.test.context.TestComponent;

/**
 * Imports the MINI instance, plans it and publishes the result, so an execution test starts from a
 * real published plan rather than hand-built rows.
 *
 * <p>MINI is three customers with windows at 50-60, 116-126 and 161-171 minutes, and one vehicle can
 * serve all three in that order. The solver minimises vehicles first, so it reliably produces one
 * plan with three stops — which is what makes it a usable fixture for a route being executed.
 */
@TestComponent
public class PublishedPlanFixture {

    public static final LocalDate SERVICE_DATE = LocalDate.of(2026, 1, 5);

    private final SolomonImportService importer;
    private final PlanRunService runs;
    private final PlanQueryService queries;
    private final PlanPublisher publisher;

    PublishedPlanFixture(
            SolomonImportService importer, PlanRunService runs, PlanQueryService queries, PlanPublisher publisher) {
        this.importer = importer;
        this.runs = runs;
        this.queries = queries;
        this.publisher = publisher;
    }

    /** A published plan with three stops, in service order. */
    public PlanResponse publishedPlan() {
        final var imported = importer.importFrom(instance(), SERVICE_DATE);
        final var runId = runs.submit(new PlanRunRequest(imported.depotId(), SERVICE_DATE, 2, 42L));
        final var run = awaitRun(runId);

        final var biggest = run.planIds().stream()
                .map(queries::findPlan)
                .max(java.util.Comparator.comparingInt(plan -> plan.stops().size()))
                .orElseThrow(() -> new IllegalStateException("the run produced no plans"));

        publisher.publish(biggest.id());
        return queries.findPlan(biggest.id());
    }

    /**
     * The solve runs on a worker, so a test that wants its plans has to wait for it. Polling rather
     * than a latch, because the point is to go through the same {@code GET} a real client would.
     */
    public PlanRunResponse awaitRun(Long runId) {
        final var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            final var run = queries.findRun(runId);
            if (run.status() != PlanRunStatus.RUNNING) {
                if (run.status() == PlanRunStatus.FAILED) {
                    throw new IllegalStateException("Plan run failed: " + run.failureReason());
                }
                return run;
            }
            sleepBriefly();
        }
        throw new IllegalStateException("Plan run " + runId + " was still RUNNING after 30s");
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static Reader instance() {
        final var stream = Objects.requireNonNull(
                PublishedPlanFixture.class.getResourceAsStream("/solomon/MINI.txt"), "fixture missing");
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }
}
