package dev.zeralius.clowder.planning.solver;

/**
 * A solution plus everything needed to reproduce it. M4 stores {@code config} and {@code
 * solverVersion} on {@code plan_run} so a published plan can be explained months later.
 */
public record SolverResult(
        Solution solution, SolverConfig config, long elapsedMillis, int iterations, String solverVersion) {}
