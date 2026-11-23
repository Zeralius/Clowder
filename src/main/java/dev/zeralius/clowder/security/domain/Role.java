package dev.zeralius.clowder.security.domain;

/**
 * Who someone is, in the only three shapes this business has.
 *
 * <p>One role per user rather than a set. A dispatcher who is also a driver is not a case that
 * exists here, and a many-to-many invented before anything needs it is complexity with no customer.
 *
 * <p>The authority name is the role with the {@code ROLE_} prefix Spring Security expects, so that
 * {@code hasRole("DISPATCHER")} in the filter chain and {@code DISPATCHER} in the database are the
 * same thing rather than two spellings that have to be kept in step.
 */
public enum Role {
    /** Fleet reference data: depots, vehicles, drivers, and user accounts. */
    ADMIN,

    /** Plans the day, publishes, and may correct any driver report. */
    DISPATCHER,

    /** Reports what happened, and only against their own plans. */
    DRIVER;

    public String authority() {
        return "ROLE_" + name();
    }
}
