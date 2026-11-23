package dev.zeralius.clowder.execution;

import dev.zeralius.clowder.importer.SolomonImportService;
import dev.zeralius.clowder.planning.plan.api.PlanResponse;
import dev.zeralius.clowder.planning.plan.api.PlanRunRequest;
import dev.zeralius.clowder.planning.plan.domain.PlanPublisher;
import dev.zeralius.clowder.planning.plan.domain.PlanQueryService;
import dev.zeralius.clowder.planning.plan.domain.PlanRunService;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
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
            SolomonImportService importer,
            PlanRunService runs,
            PlanQueryService queries,
            PlanPublisher publisher) {
        this.importer = importer;
        this.runs = runs;
        this.queries = queries;
        this.publisher = publisher;
    }

    /** A published plan with three stops, in service order. */
    public PlanResponse publishedPlan() {
        final var imported = importer.importFrom(instance(), SERVICE_DATE);
        final var runId = runs.execute(new PlanRunRequest(imported.depotId(), SERVICE_DATE, 2, 42L));
        final var run = queries.findRun(runId);

        final var biggest = run.planIds().stream()
                .map(queries::findPlan)
                .max(java.util.Comparator.comparingInt(plan -> plan.stops().size()))
                .orElseThrow(() -> new IllegalStateException("the run produced no plans"));

        publisher.publish(biggest.id());
        return queries.findPlan(biggest.id());
    }

    private static Reader instance() {
        final var stream = Objects.requireNonNull(
                PublishedPlanFixture.class.getResourceAsStream("/solomon/MINI.txt"), "fixture missing");
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }
}
