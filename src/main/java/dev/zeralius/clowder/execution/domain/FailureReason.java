package dev.zeralius.clowder.execution.domain;

/**
 * Why a delivery did not happen. A normal outcome with a reason, not an error: modelling this as an
 * exception path is how a system ends up unable to represent an ordinary Tuesday.
 */
public enum FailureReason {
    CUSTOMER_ABSENT,
    REFUSED,
    ADDRESS_INVALID,
    ACCESS_DENIED,
    OTHER
}
