package dev.zeralius.clowder.planning.plan.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * <pre>
 *   DRAFT ──→ PUBLISHED ──→ SUPERSEDED
 *     │
 *     └────→ CANCELLED
 * </pre>
 *
 * <p>A published plan is never edited and never un-published. Replanning creates the next version and
 * supersedes the old one, which is what makes a plan an artifact rather than a row that drifts.
 *
 * <p>Same {@link EnumMap} shape as {@code ConsignmentStatus}, and for the same reason: the legal moves
 * belong next to the states, not spread across the service that happens to make them.
 */
public enum PlanStatus {
    DRAFT,
    PUBLISHED,
    SUPERSEDED,
    CANCELLED;

    private static final Map<PlanStatus, Set<PlanStatus>> ALLOWED;

    static {
        final var allowed = new EnumMap<PlanStatus, Set<PlanStatus>>(PlanStatus.class);
        allowed.put(DRAFT, EnumSet.of(PUBLISHED, CANCELLED));
        allowed.put(PUBLISHED, EnumSet.of(SUPERSEDED));
        allowed.put(SUPERSEDED, EnumSet.noneOf(PlanStatus.class));
        allowed.put(CANCELLED, EnumSet.noneOf(PlanStatus.class));
        ALLOWED = Collections.unmodifiableMap(allowed);
    }

    public boolean canTransitionTo(PlanStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }
}
