package dev.zeralius.clowder.consignment.domain;

import dev.zeralius.clowder.common.error.ConflictException;
import dev.zeralius.clowder.common.geo.LatLon;
import dev.zeralius.clowder.common.time.TimeWindow;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * The constructor takes value objects but the columns are flat. The database wants six scalar
 * columns; the domain wants two invariants. The constructor is the seam — an invalid coordinate or a
 * backwards window cannot get past it.
 */
@Entity
@Table(name = "consignment")
public class Consignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String reference;

    /**
     * A plain id, not a {@code @ManyToOne}. {@code depot} belongs to the fleet module; crossing that
     * boundary in the object graph is what the facade exists to prevent.
     */
    @Column(name = "depot_id", nullable = false)
    private Long depotId;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "address_line", nullable = false, length = 256)
    private String addressLine;

    @Column(name = "weight_grams", nullable = false)
    private long weightGrams;

    @Column(name = "volume_millilitres", nullable = false)
    private long volumeMillilitres;

    @Column(name = "service_minutes", nullable = false)
    private int serviceMinutes;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    /** STRING, never ORDINAL: reordering the enum must not rewrite the meaning of stored rows. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ConsignmentStatus status = ConsignmentStatus.PENDING;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    /** For JPA. */
    protected Consignment() {}

    public Consignment(
            String reference,
            Long depotId,
            LocalDate serviceDate,
            LatLon location,
            String addressLine,
            long weightGrams,
            long volumeMillilitres,
            int serviceMinutes,
            TimeWindow window) {
        this.reference = reference;
        this.depotId = depotId;
        this.serviceDate = serviceDate;
        this.latitude = location.latitude();
        this.longitude = location.longitude();
        this.addressLine = addressLine;
        this.weightGrams = weightGrams;
        this.volumeMillilitres = volumeMillilitres;
        this.serviceMinutes = serviceMinutes;
        this.windowStart = window.start();
        this.windowEnd = window.end();
        this.status = ConsignmentStatus.PENDING;
    }

    /** The only way status changes. An illegal move is a 409, not an inconsistent row. */
    public void transitionTo(ConsignmentStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new ConflictException(
                    "Consignment '%s' cannot move from %s to %s".formatted(reference, status, target));
        }
        this.status = target;
    }

    public void reschedule(TimeWindow window) {
        this.windowStart = window.start();
        this.windowEnd = window.end();
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public Long getDepotId() {
        return depotId;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    /** Rebuilt per call. The flat columns are a persistence detail callers should never see. */
    public LatLon getLocation() {
        return new LatLon(latitude, longitude);
    }

    public String getAddressLine() {
        return addressLine;
    }

    public long getWeightGrams() {
        return weightGrams;
    }

    public long getVolumeMillilitres() {
        return volumeMillilitres;
    }

    public int getServiceMinutes() {
        return serviceMinutes;
    }

    public TimeWindow getWindow() {
        return new TimeWindow(windowStart, windowEnd);
    }

    public ConsignmentStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
