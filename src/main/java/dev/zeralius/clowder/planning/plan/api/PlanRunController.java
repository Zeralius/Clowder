package dev.zeralius.clowder.planning.plan.api;

import dev.zeralius.clowder.planning.plan.domain.PlanningService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Solves a depot-day and returns the routes.
 *
 * <p>Synchronous and stateless for now: nothing is persisted, so there is no {@code GET
 * /api/plan-runs/{id}} to poll and no 202. M4 adds {@code plan_run}, versioned plans and the
 * publish rule, and this becomes the async-shaped resource the tracker describes. The request shape
 * is the one that survives that change.
 */
@RestController
@RequestMapping("/api/plan-runs")
public class PlanRunController {

    private final PlanningService planning;

    PlanRunController(PlanningService planning) {
        this.planning = planning;
    }

    @PostMapping
    PlanRunResponse plan(@Valid @RequestBody PlanRunRequest request) {
        return planning.plan(request);
    }

    /** The same solve, rendered as SVG. Re-solves, because there is nothing stored to read back. */
    @PostMapping(value = "/svg", produces = "image/svg+xml")
    String svg(@Valid @RequestBody PlanRunRequest request) {
        return planning.planAsSvg(request);
    }
}
