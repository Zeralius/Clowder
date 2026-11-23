package dev.zeralius.clowder.planning.plan.domain;

import dev.zeralius.clowder.planning.plan.api.PlanRunRequest;
import dev.zeralius.clowder.planning.solver.Solver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a plan run: load, solve, persist.
 *
 * <p><b>Deliberately not {@code @Transactional}.</b> The solve in the middle is CPU-bound for up to
 * the budget the request asked for, and holding a pooled connection across it is how a connection
 * pool is exhausted under load. The three phases live on three beans so that Spring proxies actually
 * apply; see {@link PlanRunPersister}.
 *
 * <p><b>Why loading happens before the run row exists.</b> An unknown depot or a depot with no active
 * vehicles is a bad request, not a failed solve, and it deserves a 404 rather than an accepted run
 * that reports FAILED a moment later. Once the problem has loaded, everything after it is recorded:
 * a run that blows up mid-solve leaves a FAILED row with a reason on it.
 */
@Service
public class PlanRunService {

    private static final Logger log = LoggerFactory.getLogger(PlanRunService.class);

    private final PlanningProblemLoader loader;
    private final PlanRunPersister persister;
    private final Solver solver;

    public PlanRunService(PlanningProblemLoader loader, PlanRunPersister persister, Solver solver) {
        this.loader = loader;
        this.persister = persister;
        this.solver = solver;
    }

    public Long execute(PlanRunRequest request) {
        final var loaded = loader.load(request.depotId(), request.serviceDate());
        final var config = SolverConfigs.from(request);

        final var run = persister.startRun(
                request.depotId(),
                request.serviceDate(),
                solver.version(),
                config.seed(),
                Math.toIntExact(config.budget().toMillis()));

        try {
            // No transaction is open here. That is the point.
            final var result = solver.solve(loaded.instance(), config);

            final var drafts = persister.saveSuccess(run.getId(), result, loaded, request.serviceDate());
            log.info("Plan run {} produced {} draft plans", run.getId(), drafts.size());

        } catch (RuntimeException e) {
            // Accepted for processing, so a FAILED run with a reason is a better answer than a 500.
            // That is the async shape doing its job before there is anything async about it.
            log.warn("Plan run {} failed", run.getId(), e);
            persister.saveFailure(run.getId(), e.getMessage());
        }

        return run.getId();
    }
}
