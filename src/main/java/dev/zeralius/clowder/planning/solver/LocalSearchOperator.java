package dev.zeralius.clowder.planning.solver;

/**
 * One improvement move. Implementations are stateless and apply the <em>first</em> improving move they
 * find, scanning in a fixed order — first-improvement rather than best-improvement, because it is
 * faster per pass and trivially deterministic.
 */
interface LocalSearchOperator {

    String name();

    /** Applies one improving move if it can find one. Returns whether it changed anything. */
    boolean improve(SolutionState state);
}
