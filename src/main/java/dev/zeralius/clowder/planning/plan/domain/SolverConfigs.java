package dev.zeralius.clowder.planning.plan.domain;

import dev.zeralius.clowder.planning.plan.api.PlanRunRequest;
import dev.zeralius.clowder.planning.solver.SolverConfig;
import java.time.Duration;

/** Turns a request into a solver config, so the stored run and the rendered one tune identically. */
final class SolverConfigs {

    private SolverConfigs() {}

    static SolverConfig from(PlanRunRequest request) {
        final var defaults = SolverConfig.defaults();
        return new SolverConfig(
                request.seed() == null ? defaults.seed() : request.seed(),
                request.budgetSeconds() == null ? defaults.budget() : Duration.ofSeconds(request.budgetSeconds()),
                defaults.insertionAlpha(),
                defaults.insertionLambda(),
                defaults.maxNoImprovement());
    }
}
