package dev.zeralius.clowder.security.persistence;

import java.time.Instant;

/**
 * The two columns the request path needs to decide whether a token is still good.
 *
 * <p>A projection rather than the entity, for two reasons. It reads two columns instead of a row on
 * every authenticated request, and it hands the code that runs inside a security filter something it
 * cannot accidentally modify: a managed entity loaded there would be a managed entity that something
 * could change outside any transaction it meant to open.
 */
public interface TokenValidity {

    boolean isActive();

    Instant getTokensValidFrom();
}
