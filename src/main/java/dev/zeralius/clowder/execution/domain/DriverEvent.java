package dev.zeralius.clowder.execution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * An immutable record of something a driver reported.
 *
 * <p>Append-only, so there are no setters at all — not "no status setter" as on {@code Consignment},
 * none. The row is written once and never touched again, and a correction is a compensating event. An
 * instance method that changed a field here would have nothing legitimate to do.
 *
 * <p>{@code planId} and {@code planStopId} are plain ids: planning is another module, and execution
 * reads it through {@code PlanningFacade} rather than holding its entities.
 */
@Entity
@Table(name = "driver_event")
public class DriverEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /** Null for route-scoped events such as DEPARTED_DEPOT. */
    @Column(name = "plan_stop_id")
    private Long planStopId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 24)
    private DriverEventType eventType;

    /** The device clock. Wrong more often than you would like, so it is used only for ordering. */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** The server clock. This is what lateness and connectivity are reasoned about. */
    @Column(name = "received_at", nullable = false, insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant receivedAt;

    /**
     * Advisory only, and never used to decide a transition. Geofenced auto-arrival produces spurious
     * events in car parks, so what a driver reported is what counts, not where the phone thinks it is.
     */
    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 64)
    private FailureReason failureReason;

    /** For JPA. */
    protected DriverEvent() {}

    public DriverEvent(
            String idempotencyKey,
            Long planId,
            Long planStopId,
            DriverEventType eventType,
            Instant occurredAt,
            BigDecimal latitude,
            BigDecimal longitude,
            FailureReason failureReason) {
        this.idempotencyKey = idempotencyKey;
        this.planId = planId;
        this.planStopId = planStopId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.latitude = latitude;
        this.longitude = longitude;
        this.failureReason = failureReason;
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getPlanId() {
        return planId;
    }

    public Long getPlanStopId() {
        return planStopId;
    }

    public DriverEventType getEventType() {
        return eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }
}
