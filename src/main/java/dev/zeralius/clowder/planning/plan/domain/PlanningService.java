package dev.zeralius.clowder.planning.plan.domain;

import dev.zeralius.clowder.planning.plan.api.PlanRunRequest;
import dev.zeralius.clowder.planning.render.RoutePlanSvgRenderer;
import dev.zeralius.clowder.planning.solver.Solver;
import org.springframework.stereotype.Service;

/**
 * Renders a depot-day as a picture.
 *
 * <p>It re-solves rather than reading stored plans back, because the drawing needs the coordinates and
 * the distance matrix and neither is persisted: a plan records what was decided, not the geometry it
 * was decided from. With the same seed and budget the answer is the same one a stored run produced,
 * which is what makes the picture in the README reproducible.
 *
 * <p><b>Deliberately not {@code @Transactional}</b>, for the reason in {@link PlanRunService}.
 */
@Service
public class PlanningService {

    private final PlanningProblemLoader loader;
    private final Solver solver;
    private final RoutePlanSvgRenderer renderer = new RoutePlanSvgRenderer();

    public PlanningService(PlanningProblemLoader loader, Solver solver) {
        this.loader = loader;
        this.solver = solver;
    }

    public String planAsSvg(PlanRunRequest request) {
        final var loaded = loader.load(request.depotId(), request.serviceDate());
        final var result = solver.solve(loaded.instance(), SolverConfigs.from(request));
        return renderer.render(result.solution(), loaded.instance(), loaded.points(), 0);
    }
}
