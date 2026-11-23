package dev.zeralius.clowder.fleet.domain;

import dev.zeralius.clowder.common.geo.DistanceModel;
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
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "depot")
public class Depot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(nullable = false, length = 64)
    private String timezone;

    /** Which geometry this depot coordinates are in. See {@link DistanceModel}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "distance_model", nullable = false, length = 16)
    private DistanceModel distanceModel;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private long version;

    /** Written by the database default, read back on insert, never updated from Java. */
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    /** For JPA. */
    protected Depot() {}

    public Depot(
            String code,
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            String timezone,
            DistanceModel distanceModel) {
        this.code = code;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timezone = timezone;
        this.distanceModel = distanceModel;
        this.active = true;
    }

    /**
     * Named mutators, no setters, same as every other entity here. The code is not among them: it is
     * how imports and operators identify a depot, and renaming an identifier is a different operation
     * from correcting its details.
     */
    public void relocate(BigDecimal latitude, BigDecimal longitude, DistanceModel distanceModel) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanceModel = distanceModel;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void moveToTimezone(String timezone) {
        this.timezone = timezone;
    }

    /** Deactivating is reversible and keeps history. Depots are referenced by plans that still matter. */
    public void deactivate() {
        this.active = false;
    }

    public void reactivate() {
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getTimezone() {
        return timezone;
    }

    public DistanceModel getDistanceModel() {
        return distanceModel;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
