package dev.zeralius.clowder.execution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The plan-level half of the materialised projection.
 *
 * <p>Every field is derived from the event log. Nothing writes these numbers except the projection
 * updater, and {@code eventsApplied} records how much of the log they came from, which is what makes
 * a stale row obvious instead of merely wrong.
 */
@Entity
@Table(name = "plan_execution_state")
public class PlanExecutionState {

    @Id
    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "stops_completed", nullable = false)
    private int stopsCompleted;

    @Column(name = "stops_remaining", nullable = false)
    private int stopsRemaining;

    @Column(name = "current_delay_minutes", nullable = false)
    private int currentDelayMinutes;

    @Column(name = "last_known_departure")
    private Instant lastKnownDeparture;

    @Column(name = "last_completed_stop")
    private Long lastCompletedStop;

    @Column(name = "events_applied", nullable = false)
    private int eventsApplied;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** For JPA. */
    protected PlanExecutionState() {}

    public PlanExecutionState(Long planId) {
        this.planId = planId;
    }

    /** One write path, taking a whole projection: a partial update here would be a bug waiting. */
    public void replaceWith(PlanProjection projection, int eventsApplied, Instant updatedAt) {
        this.stopsCompleted = projection.completed();
        this.stopsRemaining = projection.remaining();
        this.currentDelayMinutes = projection.currentDelayMinutes();
        this.lastKnownDeparture = projection.lastKnownDeparture();
        this.lastCompletedStop = projection.lastCompletedStopId();
        this.eventsApplied = eventsApplied;
        this.updatedAt = updatedAt;
    }

    public Long getPlanId() {
        return planId;
    }

    public int getStopsCompleted() {
        return stopsCompleted;
    }

    public int getStopsRemaining() {
        return stopsRemaining;
    }

    public int getCurrentDelayMinutes() {
        return currentDelayMinutes;
    }

    public Instant getLastKnownDeparture() {
        return lastKnownDeparture;
    }

    public Long getLastCompletedStop() {
        return lastCompletedStop;
    }

    public int getEventsApplied() {
        return eventsApplied;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
