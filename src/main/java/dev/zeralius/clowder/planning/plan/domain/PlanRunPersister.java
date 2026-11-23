package dev.zeralius.clowder.planning.plan.domain;

import dev.zeralius.clowder.planning.plan.persistence.PlanRepository;
import dev.zeralius.clowder.planning.plan.persistence.PlanRunRepository;
import dev.zeralius.clowder.planning.solver.SolverResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two short write transactions that bracket a solve.
 *
 * <p>This is a separate bean from {@link PlanRunService} on purpose. Spring transactions are
 * proxy-based, so a call from one method to another in the same class goes straight to the target and
 * never reaches the interceptor. Put the load, the solve and the persist on one class and you get one
 * long transaction spanning the solve, or none at all, with nothing to warn you and tests that still
 * pass. Three beans, three proxies, three real boundaries.
 */
@Service
public class PlanRunPersister {

    private final PlanRunRepository runs;
    private final PlanRepository plans;
    private final PlanAssembler assembler = new PlanAssembler();

    public PlanRunPersister(PlanRunRepository runs, PlanRepository plans) {
        this.runs = runs;
        this.plans = plans;
    }

    @Transactional
    public PlanRun startRun(Long depotId, LocalDate serviceDate, String solverVersion, long seed, int budgetMs) {
        return runs.save(new PlanRun(depotId, serviceDate, solverVersion, seed, budgetMs));
    }

    /** One transaction: either the run is SUCCEEDED with all of its plans, or none of it happened. */
    @Transactional
    public List<Plan> saveSuccess(
            Long runId, SolverResult result, PlanningProblemLoader.LoadedProblem loaded, LocalDate serviceDate) {

        final var run = runs.findById(runId).orElseThrow();

        final var drafts = assembler.assemble(
                runId,
                result.solution(),
                loaded.instance(),
                loaded.epoch(),
                serviceDate,
                loaded.consignments(),
                vehicleId -> plans.findMaxVersionNo(vehicleId, serviceDate) + 1);

        final var saved = plans.saveAll(drafts);

        final var couldNotRoute = new ArrayList<UnassignedConsignment>();
        for (final var position : result.solution().unassigned()) {
            final var consignment = loaded.consignments().get(position);
            couldNotRoute.add(new UnassignedConsignment(consignment.id(), consignment.reference()));
        }

        run.succeed(result.elapsedMillis(), result.iterations(), couldNotRoute);
        return saved;
    }

    /**
     * {@code REQUIRES_NEW} so the failure record survives even when the caller context is being
     * unwound. A failed run that leaves no trace is the same as no run at all.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFailure(Long runId, String reason) {
        runs.findById(runId).ifPresent(run -> run.fail(reason));
    }
}
