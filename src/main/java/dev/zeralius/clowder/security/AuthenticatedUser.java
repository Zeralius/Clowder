package dev.zeralius.clowder.security;

import dev.zeralius.clowder.security.domain.Role;

/**
 * Who is making the current request, as the rest of the application needs to know it.
 *
 * <p>A record, not the entity and not Spring Security's {@code Authentication}: a module asking
 * "which driver is this?" should not have to understand either.
 *
 * @param driverId the driver this account is, or null for a dispatcher or an admin
 */
public record AuthenticatedUser(String username, Role role, Long driverId) {

    public boolean isDriver() {
        return role == Role.DRIVER;
    }

    /** True when this user may act without ownership checks: dispatchers correct, admins administer. */
    public boolean actsOnBehalfOfAnyone() {
        return role == Role.DISPATCHER || role == Role.ADMIN;
    }

    /**
     * Whether this caller may read or report against a plan assigned to {@code planDriverId}.
     *
     * <p>The one authorisation question in this system that a URL pattern cannot answer: the plan id
     * is in the path and whose plan it is lives in the database. It is here rather than in either
     * caller so that planning and execution cannot drift apart on what the rule is.
     *
     * <p>A dispatcher or an admin may act on any plan — a dispatcher entering events for a driver
     * whose phone died is normal work, not a workaround. A driver may act on a plan assigned to them,
     * and an unassigned plan belongs to no driver, so no driver may touch it.
     */
    public boolean mayActOnPlanAssignedTo(Long planDriverId) {
        if (actsOnBehalfOfAnyone()) {
            return true;
        }
        return driverId != null && driverId.equals(planDriverId);
    }
}
