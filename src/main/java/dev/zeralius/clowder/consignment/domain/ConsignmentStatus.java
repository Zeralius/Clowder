package dev.zeralius.clowder.consignment.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The consignment lifecycle, as a state machine rather than a string.
 *
 * <pre>
 *   PENDING ──→ PLANNED ──→ DISPATCHED ──→ DELIVERED
 *      ↑           │             │
 *      └───────────┘             └──────→ FAILED ──→ PENDING
 *      │           │
 *      └── CANCELLED ←┘
 * </pre>
 *
 * <p>An {@code EnumMap}, not {@code Map.of}: {@code Map.of} has deliberately randomised iteration
 * order, and M3's determinism requirement is exactly the kind of thing that gets quietly broken by a
 * collection whose order changes per JVM run.
 */
public enum ConsignmentStatus {
    PENDING,
    PLANNED,
    DISPATCHED,
    DELIVERED,
    FAILED,
    CANCELLED;

    private static final Map<ConsignmentStatus, Set<ConsignmentStatus>> ALLOWED;

    static {
        var allowed = new EnumMap<ConsignmentStatus, Set<ConsignmentStatus>>(ConsignmentStatus.class);
        allowed.put(PENDING, EnumSet.of(PLANNED, CANCELLED));
        // PLANNED -> PENDING is "unplan"; the dispatcher pulled it off a draft route.
        allowed.put(PLANNED, EnumSet.of(DISPATCHED, PENDING, CANCELLED));
        allowed.put(DISPATCHED, EnumSet.of(DELIVERED, FAILED));
        allowed.put(DELIVERED, EnumSet.noneOf(ConsignmentStatus.class));
        // FAILED -> PENDING is "retry tomorrow"; M5 depends on it.
        allowed.put(FAILED, EnumSet.of(PENDING));
        allowed.put(CANCELLED, EnumSet.noneOf(ConsignmentStatus.class));
        ALLOWED = Collections.unmodifiableMap(allowed);
    }

    public boolean canTransitionTo(ConsignmentStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public Set<ConsignmentStatus> allowedTargets() {
        return ALLOWED.get(this);
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }
}
