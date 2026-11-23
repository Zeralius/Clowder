package dev.zeralius.clowder.planning.plan.api;

import dev.zeralius.clowder.planning.plan.domain.PlanQueryService;
import dev.zeralius.clowder.planning.plan.domain.PlanRunService;
import dev.zeralius.clowder.planning.plan.domain.PlanningService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Solves a depot-day, stores the result as draft plans, and hands back the run.
 *
 * <p><b>Why 202 and a Location header when the work has already finished.</b> The shape is what a
 * worker needs, and changing an API contract later is expensive while writing it this way now is
 * free. A client that polls {@code GET /api/plan-runs/{id}} keeps working unchanged on the day the
 * solve moves off the request thread.
 */
@RestController
@RequestMapping("/api/plan-runs")
public class PlanRunController {

    private final PlanRunService runs;
    private final PlanQueryService queries;
    private final PlanningService rendering;

    PlanRunController(PlanRunService runs, PlanQueryService queries, PlanningService rendering) {
        this.runs = runs;
        this.queries = queries;
        this.rendering = rendering;
    }

    @PostMapping
    ResponseEntity<PlanRunResponse> create(@Valid @RequestBody PlanRunRequest request) {
        final var runId = runs.execute(request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/plan-runs/" + runId))
                .body(queries.findRun(runId));
    }

    @GetMapping("/{id}")
    PlanRunResponse findById(@PathVariable Long id) {
        return queries.findRun(id);
    }

    /** The same solve, drawn. Re-solves: the coordinates a picture needs are not part of a plan. */
    @PostMapping(value = "/svg", produces = "image/svg+xml")
    String svg(@Valid @RequestBody PlanRunRequest request) {
        return rendering.planAsSvg(request);
    }
}
