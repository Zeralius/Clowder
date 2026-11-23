package dev.zeralius.clowder.fleet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "vehicle")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;

    @Column(nullable = false, unique = true, length = 16)
    private String registration;

    @Column(name = "capacity_grams", nullable = false)
    private long capacityGrams;

    @Column(name = "capacity_millilitres", nullable = false)
    private long capacityMillilitres;

    @Column(name = "shift_start", nullable = false)
    private LocalTime shiftStart;

    /**
     * Shift length in minutes rather than an end time (decision D1). A {@code time} column cannot
     * express a Solomon type-2 horizon (C201 runs to roughly 3390) or an overnight shift.
     */
    @Column(name = "shift_minutes", nullable = false)
    private int shiftMinutes;

    @Column(name = "avg_speed_kph", nullable = false, precision = 5, scale = 2)
    private BigDecimal avgSpeedKph;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    /** For JPA. */
    protected Vehicle() {}

    public Vehicle(
            Depot depot,
            String registration,
            long capacityGrams,
            long capacityMillilitres,
            LocalTime shiftStart,
            int shiftMinutes,
            BigDecimal avgSpeedKph) {
        this.depot = depot;
        this.registration = registration;
        this.capacityGrams = capacityGrams;
        this.capacityMillilitres = capacityMillilitres;
        this.shiftStart = shiftStart;
        this.shiftMinutes = shiftMinutes;
        this.avgSpeedKph = avgSpeedKph;
    }

    public void updateCapacity(long grams, long millilitres) {
        this.capacityGrams = grams;
        this.capacityMillilitres = millilitres;
    }

    public void updateShift(LocalTime start, int minutes) {
        this.shiftStart = start;
        this.shiftMinutes = minutes;
    }

    public void updateAvgSpeed(BigDecimal kph) {
        this.avgSpeedKph = kph;
    }

    public void deactivate() {
        this.active = false;
    }

    public void reactivate() {
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public Depot getDepot() {
        return depot;
    }

    public String getRegistration() {
        return registration;
    }

    public long getCapacityGrams() {
        return capacityGrams;
    }

    public long getCapacityMillilitres() {
        return capacityMillilitres;
    }

    public LocalTime getShiftStart() {
        return shiftStart;
    }

    public int getShiftMinutes() {
        return shiftMinutes;
    }

    public BigDecimal getAvgSpeedKph() {
        return avgSpeedKph;
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
