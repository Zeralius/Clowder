package dev.zeralius.clowder.planning.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One visit in a plan, with the times that were planned frozen in place.
 *
 * <p><b>Snapshot, not reference.</b> When a consignment is edited next week — a new window, a
 * corrected address — this plan must still show what was planned, which is the reasoning that makes
 * an invoice immutable when a price list changes. The reference and address line are copied here for
 * the same reason, so reading a plan back never depends on the current state of the order.
 *
 * <p>That is also why {@code consignmentId} is a plain {@code Long}: consignment is another module,
 * and the boundary rule has not changed since M1.
 */
@Entity
@Table(name = "plan_stop")
public class PlanStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    /** A plain id: consignment is another module. */
    @Column(name = "consignment_id", nullable = false)
    private Long consignmentId;

    @Column(nullable = false, length = 32)
    private String reference;

    @Column(name = "address_line", nullable = false, length = 256)
    private String addressLine;

    @Column(name = "planned_arrival", nullable = false)
    private Instant plannedArrival;

    @Column(name = "planned_service_start", nullable = false)
    private Instant plannedServiceStart;

    @Column(name = "planned_departure", nullable = false)
    private Instant plannedDeparture;

    /** Time spent waiting for the window to open. Normal, never a violation. */
    @Column(name = "wait_minutes", nullable = false)
    private int waitMinutes;

    @Column(name = "cumulative_distance", nullable = false)
    private long cumulativeDistance;

    @Column(name = "load_after", nullable = false)
    private long loadAfter;

    /** For JPA. */
    protected PlanStop() {}

    public PlanStop(
            int sequenceNo,
            Long consignmentId,
            String reference,
            String addressLine,
            Instant plannedArrival,
            Instant plannedServiceStart,
            Instant plannedDeparture,
            int waitMinutes,
            long cumulativeDistance,
            long loadAfter) {
        this.sequenceNo = sequenceNo;
        this.consignmentId = consignmentId;
        this.reference = reference;
        this.addressLine = addressLine;
        this.plannedArrival = plannedArrival;
        this.plannedServiceStart = plannedServiceStart;
        this.plannedDeparture = plannedDeparture;
        this.waitMinutes = waitMinutes;
        this.cumulativeDistance = cumulativeDistance;
        this.loadAfter = loadAfter;
    }

    /** Package-private: only {@link Plan#addStop} sets the owning side. */
    void attachTo(Plan plan) {
        this.plan = plan;
    }

    public Long getId() {
        return id;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public Long getConsignmentId() {
        return consignmentId;
    }

    public String getReference() {
        return reference;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public Instant getPlannedArrival() {
        return plannedArrival;
    }

    public Instant getPlannedServiceStart() {
        return plannedServiceStart;
    }

    public Instant getPlannedDeparture() {
        return plannedDeparture;
    }

    public int getWaitMinutes() {
        return waitMinutes;
    }

    public long getCumulativeDistance() {
        return cumulativeDistance;
    }

    public long getLoadAfter() {
        return loadAfter;
    }
}
