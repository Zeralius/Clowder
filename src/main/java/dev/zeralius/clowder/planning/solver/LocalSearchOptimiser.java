package dev.zeralius.clowder.planning.solver;

import dev.zeralius.clowder.planning.model.ProblemInstance;
import java.util.List;
import java.util.Random;

/**
 * Iterated local search: drive the operators to a local optimum, then kick the solution and do it
 * again, keeping the best answer seen.
 *
 * <p>Operator order is part of the contract, and it follows the objective. Route elimination comes
 * first because retiring a vehicle beats any distance saving, then relocate, then the intra-route
 * operators to tidy up what the other two left behind.
 *
 * <p>The kick exists because without it the search finished in about 20 ms and left the rest of its
 * budget unused. Every kick is drawn from a {@link Random} seeded from the config, so the same seed
 * still gives the same answer.
 */
final class LocalSearchOptimiser {

    /** How many stops a kick pulls out, as a share of the instance. Small enough to stay in the basin. */
    private static final double RUIN_FRACTION = 0.15d;

    private static final int MIN_RUIN = 2;
    private static final int MAX_RUIN = 40;

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
        final var random = new Random(config.seed());
        final var ruinSize = ruinSize();

        var state = SolutionState.of(initial, instance);
        descend(state, deadline);

        var best = state.toSolution();
        if (!best.isBetterThan(initial)) {
            best = initial;
        }

        var iterations = 1;
        var withoutImprovement = 0;

        while (!deadline.hasExpired() && withoutImprovement < config.maxNoImprovement()) {
            if (!RuinAndRecreate.perturb(state, random, ruinSize)) {
                // Nothing left to rearrange, so another kick would be the same kick.
                break;
            }
            descend(state, deadline);
            iterations++;

            final var candidate = state.toSolution();
            if (candidate.isBetterThan(best)) {
                best = candidate;
                withoutImprovement = 0;
            } else {
                // Go back to the best known arrangement rather than wandering off it.
                state = SolutionState.of(best, instance);
                withoutImprovement++;
            }
        }

        return new Outcome(best, iterations);
    }

    /** Runs every operator to exhaustion, in order, until a whole pass changes nothing. */
    private void descend(SolutionState state, Deadline deadline) {
        var changed = true;
        while (changed && !deadline.hasExpired()) {
            changed = false;
            for (var operator : operators) {
                while (operator.improve(state)) {
                    changed = true;
                    if (deadline.hasExpired()) {
                        return;
                    }
                }
            }
        }
    }

    private int ruinSize() {
        final var scaled = (int) Math.round(instance.stopCount() * RUIN_FRACTION);
        return Math.max(MIN_RUIN, Math.min(MAX_RUIN, scaled));
    }

    record Outcome(Solution solution, int iterations) {}
}
