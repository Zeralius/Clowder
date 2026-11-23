package dev.zeralius.clowder.execution.domain;

/**
 * What a driver reported. The declaration order is documentation only: the projector uses explicit
 * rules, because events do not arrive in this order and must not be assumed to.
 */
public enum DriverEventType {
    DEPARTED_DEPOT,
    ARRIVED,
    DELIVERED,
    FAILED,
    DEPARTED,
    RETURNED_DEPOT;

    /** DEPARTED_DEPOT and RETURNED_DEPOT belong to the route, not to any one stop. */
    public boolean isStopScoped() {
        return this != DEPARTED_DEPOT && this != RETURNED_DEPOT;
    }

    public boolean isTerminalForStop() {
        return this == DELIVERED || this == FAILED;
    }
}
