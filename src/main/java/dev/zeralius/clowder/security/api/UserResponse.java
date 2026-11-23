package dev.zeralius.clowder.security.api;

import dev.zeralius.clowder.security.domain.Role;

/** Everything about an account except anything to do with its password. */
public record UserResponse(Long id, String username, Role role, Long driverId, boolean active) {}
