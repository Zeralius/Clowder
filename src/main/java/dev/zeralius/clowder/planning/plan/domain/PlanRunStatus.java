package dev.zeralius.clowder.planning.plan.domain;

/**
 * A run is accepted, then it either produced plans or it did not. There is no partial outcome: the
 * persist phase is one transaction, so a run that failed left no plans behind.
 */
public enum PlanRunStatus {
    RUNNING,
    SUCCEEDED,
    FAILED
}
