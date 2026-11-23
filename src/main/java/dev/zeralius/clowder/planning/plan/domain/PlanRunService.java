package dev.zeralius.clowder.planning.plan.domain;

import dev.zeralius.clowder.planning.plan.api.PlanRunRequest;
import dev.zeralius.clowder.planning.solver.Solver;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a plan run: load, solve, persist.
 *
 * <p><b>The solve happens on a worker, not on the request thread.</b> It is CPU-bound for up to the
 * budget the request asked for, so a five second budget used to mean a five second HTTP request and a
 * pooled connection held across it. {@code POST /api/plan-runs} now returns as soon as the run row
 * exists, and the caller polls {@code GET /api/plan-runs/{id}} — which is the shape the resource has
 * advertised since M4, so nothing about the contract changed when the execution did.
 *
 * <p>This class is not {@code @Transactional} and the three phases live on three beans, so that
 * Spring proxies actually apply; see {@link PlanRunPersister}.
 *
 * <p><b>Why loading still happens on the request thread.</b> An unknown depot, a closed one, or a
 * depot with no active vehicles is a bad request, and the caller should hear about it in the response
 * to the request that made it rather than by polling for a FAILED run. Loading is a short read-only
 * transaction; it is the solve that was worth moving. Everything after it is recorded on the run.
 */
@Service
public class PlanRunService {

    private static final Logger log = LoggerFactory.getLogger(PlanRunService.class);

    private final PlanningProblemLoader loader;
    private final PlanRunPersister persister;
    private final Solver solver;
    private final TaskExecutor executor;

    public PlanRunService(
            PlanningProblemLoader loader,
            PlanRunPersister persister,
            Solver solver,
            @Qualifier("planRunExecutor") TaskExecutor executor) {
        this.loader = loader;
        this.persister = persister;
        this.solver = solver;
        this.executor = executor;
    }

    /** Accepts the run and returns its id. The run is RUNNING until a worker finishes with it. */
    public Long submit(PlanRunRequest request) {
        final var loaded = loader.load(request.depotId(), request.serviceDate());
        final var config = SolverConfigs.from(request);

        final var run = persister.startRun(
                request.depotId(),
                request.serviceDate(),
                solver.version(),
                config.seed(),
                Math.toIntExact(config.budget().toMillis()));

        try {
            executor.execute(() -> solveAndPersist(run.getId(), request, loaded, config));
        } catch (RejectedExecutionException e) {
            // The queue is full. Saying so on the run is the honest answer: the work was accepted,
            // then refused, and a caller polling the run finds out why instead of waiting forever.
            log.warn("Plan run {} was rejected by the worker pool", run.getId());
            persister.saveFailure(run.getId(), "The planner is busy; no worker was available");
        }

        return run.getId();
    }

    private void solveAndPersist(
            Long runId,
            PlanRunRequest request,
            PlanningProblemLoader.LoadedProblem loaded,
            dev.zeralius.clowder.planning.solver.SolverConfig config) {
        try {
            // No transaction is open on this thread. That is the point.
            final var result = solver.solve(loaded.instance(), config);

            final var drafts = persister.saveSuccess(runId, result, loaded, request.serviceDate());
            log.info("Plan run {} produced {} draft plans", runId, drafts.size());

        } catch (RuntimeException e) {
            // Accepted for processing, so a FAILED run with a reason is a better answer than a stack
            // trace nobody is waiting to read.
            log.warn("Plan run {} failed", runId, e);
            persister.saveFailure(runId, e.getMessage());
        }
    }
}
