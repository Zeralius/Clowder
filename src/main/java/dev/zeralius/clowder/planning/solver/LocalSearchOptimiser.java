package dev.zeralius.clowder.planning.solver;

import dev.zeralius.clowder.planning.model.ProblemInstance;
import java.util.List;

/**
 * Runs operators in a fixed order until nothing improves, the budget expires, or the configured number
 * of non-improving passes is reached.
 *
 * <p>Operator order is part of the contract, and it follows the objective. Route elimination comes
 * first because retiring a vehicle beats any distance saving, then relocate, then the intra-route
 * operators to tidy up what the other two left behind.
 */
final class LocalSearchOptimiser {

    private final ProblemInstance instance;
    private final SolverConfig config;
    private final List<LocalSearchOperator> operators;

    LocalSearchOptimiser(ProblemInstance instance, SolverConfig config) {
        this(
                instance,
                config,
                List.of(
                        new RouteEliminationOperator(),
                        new RelocateOperator(),
                        new TwoOptOperator(),
                        new OrOptOperator()));
    }

    LocalSearchOptimiser(ProblemInstance instance, SolverConfig config, List<LocalSearchOperator> operators) {
        this.instance = instance;
        this.config = config;
        this.operators = List.copyOf(operators);
    }

    Outcome optimise(Solution initial, Deadline deadline) {
        final var state = SolutionState.of(initial, instance);

        var best = initial;
        var iterations = 0;
        var withoutImprovement = 0;

        while (!deadline.hasExpired() && withoutImprovement < config.maxNoImprovement()) {
            var changedAnything = false;

            for (var operator : operators) {
                while (operator.improve(state)) {
                    changedAnything = true;
                    if (deadline.hasExpired()) {
                        break;
                    }
                }
                if (deadline.hasExpired()) {
                    break;
                }
            }

            iterations++;

            final var candidate = state.toSolution();
            if (candidate.isBetterThan(best)) {
                best = candidate;
                withoutImprovement = 0;
            } else {
                withoutImprovement++;
            }

            if (!changedAnything) {
                break; // local optimum for this operator set
            }
        }

        return new Outcome(best, iterations);
    }

    record Outcome(Solution solution, int iterations) {}
}
