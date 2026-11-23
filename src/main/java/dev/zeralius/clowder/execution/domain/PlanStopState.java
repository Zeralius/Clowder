package dev.zeralius.clowder.execution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** The per-stop half of the materialised projection. Derived, never authored. */
@Entity
@Table(name = "plan_stop_state")
public class PlanStopState {

    @Id
    @Column(name = "plan_stop_id")
    private Long planStopId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "consignment_id", nullable = false)
    private Long consignmentId;

    @Column(nullable = false, length = 32)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StopStatus status;

    @Column(name = "actual_arrival")
    private Instant actualArrival;

    @Column(name = "actual_departure")
    private Instant actualDeparture;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 64)
    private FailureReason failureReason;

    /** For JPA. */
    protected PlanStopState() {}

    public PlanStopState(Long planId, StopState state) {
        this.planStopId = state.planStopId();
        this.planId = planId;
        replaceWith(state);
    }

    public void replaceWith(StopState state) {
        this.consignmentId = state.consignmentId();
        this.reference = state.reference();
        this.status = state.status();
        this.actualArrival = state.actualArrival();
        this.actualDeparture = state.actualDeparture();
        this.failureReason = state.failureReason();
    }

    /** Back to the record the projector works in, so a read and a fold are interchangeable. */
    public StopState toStopState() {
        return new StopState(
                planStopId, consignmentId, reference, status, actualArrival, actualDeparture, failureReason);
    }

    public Long getPlanStopId() {
        return planStopId;
    }

    public Long getPlanId() {
        return planId;
    }

    public StopStatus getStatus() {
        return status;
    }
}
