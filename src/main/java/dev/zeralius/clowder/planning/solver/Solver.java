package dev.zeralius.clowder.planning.solver;

import dev.zeralius.clowder.planning.model.ProblemInstance;

/**
 * One method, so implementations can be swapped and compared on the same benchmark set.
 *
 * <p>Implementations take a plain problem struct and return a plain solution. No database, no Spring,
 * and never inside a transaction — a multi-second CPU-bound operation holding a pooled connection is
 * how you exhaust the pool.
 */
public interface Solver {

    SolverResult solve(ProblemInstance instance, SolverConfig config);
}
