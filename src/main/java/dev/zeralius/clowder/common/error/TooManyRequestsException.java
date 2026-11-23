package dev.zeralius.clowder.common.error;

import java.time.Duration;

/**
 * The caller has been asked to slow down. 429, with a {@code Retry-After} the caller can act on.
 *
 * <p>The retry hint travels on the exception rather than being invented by the handler, because only
 * whoever refused the request knows when the budget it exhausted comes back.
 */
public class TooManyRequestsException extends DomainException {

    private final Duration retryAfter;

    public TooManyRequestsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter.isNegative() ? Duration.ZERO : retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
