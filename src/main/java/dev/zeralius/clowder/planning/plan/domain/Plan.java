package dev.zeralius.clowder.planning.plan.domain;

import dev.zeralius.clowder.common.error.ConflictException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * An immutable routing document for one vehicle on one day.
 *
 * <p>Published plans are never edited. Replanning creates version n+1 and supersedes version n in one
 * transaction, so a dispatcher looking at v1 next month sees what v1 actually said.
 *
 * <p><b>Two locking mechanisms, two different races.</b> {@code uq_plan_published} stops two
 * <i>different</i> plans both being published for one vehicle-day — a race between rows, which no
 * select-then-insert can win. {@link Version} stops two dispatchers editing the <i>same</i> draft and
 * one silently overwriting the other — a race on one row.
 */
@Entity
@Table(name = "plan")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_run_id", nullable = false)
    private Long planRunId;

    /** The depot this vehicle works out of. Execution needs it before anything has been completed. */
    @Column(name = "depot_id", nullable = false)
    private Long depotId;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    /** The version a human reads: v1, v2. Not the optimistic-locking counter. */
    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PlanStatus status = PlanStatus.DRAFT;

    /** Minor units of the distance matrix, not metres (decision D7). */
    @Column(name = "total_distance", nullable = false)
    private long totalDistance;

    @Column(name = "total_duration_minutes", nullable = false)
    private int totalDurationMinutes;

    @Column(name = "published_at")
    private Instant publishedAt;

    /** JPA optimistic locking, deliberately not called {@code version}; see the class comment. */
    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequenceNo asc")
    private List<PlanStop> stops = new ArrayList<>();

    /** For JPA. */
    protected Plan() {}

    public Plan(
            Long planRunId,
            Long depotId,
            Long vehicleId,
            LocalDate serviceDate,
            int versionNo,
            long totalDistance,
            int totalDurationMinutes) {
        this.planRunId = planRunId;
        this.depotId = depotId;
        this.vehicleId = vehicleId;
        this.serviceDate = serviceDate;
        this.versionNo = versionNo;
        this.totalDistance = totalDistance;
        this.totalDurationMinutes = totalDurationMinutes;
        this.status = PlanStatus.DRAFT;
    }

    /** Stops may only be added while the plan is a draft. After publication it is an artifact. */
    public void addStop(PlanStop stop) {
        if (status != PlanStatus.DRAFT) {
            throw new ConflictException("Cannot add a stop to plan %s: it is %s, not DRAFT".formatted(id, status));
        }
        stop.attachTo(this);
        stops.add(stop);
    }

    public void publish() {
        transitionTo(PlanStatus.PUBLISHED);
        this.publishedAt = Instant.now();
    }

    public void supersede() {
        transitionTo(PlanStatus.SUPERSEDED);
    }

    public void cancel() {
        transitionTo(PlanStatus.CANCELLED);
    }

    private void transitionTo(PlanStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new ConflictException("Plan %s cannot move from %s to %s".formatted(id, status, target));
        }
        this.status = target;
    }

    public Long getId() {
        return id;
    }

    public Long getPlanRunId() {
        return planRunId;
    }

    public Long getDepotId() {
        return depotId;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public long getTotalDistance() {
        return totalDistance;
    }

    public int getTotalDurationMinutes() {
        return totalDurationMinutes;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public long getLockVersion() {
        return lockVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<PlanStop> getStops() {
        return Collections.unmodifiableList(stops);
    }
}
