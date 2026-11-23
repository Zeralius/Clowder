package dev.zeralius.clowder.security.api;

import dev.zeralius.clowder.security.domain.Role;
import java.time.Instant;

/**
 * @param driverId present for a driver, so a phone app knows whose plans to ask for without
 *     decoding the token itself
 */
public record TokenResponse(String token, String tokenType, Instant expiresAt, Role role, Long driverId) {}
