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
import java.time.Instant;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

@Entity
@Table(name = "driver")
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;

    @Column(name = "employee_no", nullable = false, unique = true, length = 32)
    private String employeeNo;

    @Column(name = "full_name", nullable = false, length = 128)
    private String fullName;

    @Column(name = "licence_class", nullable = false, length = 16)
    private String licenceClass;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    // JPA
    protected Driver() {}

    public Driver(Depot depot, String employeeNo, String fullName, String licenceClass) {
        this.depot = depot;
        this.employeeNo = employeeNo;
        this.fullName = fullName;
        this.licenceClass = licenceClass;
    }

    public void rename(String fullName) {
        this.fullName = fullName;
    }

    public void updateLicenceClass(String licenceClass) {
        this.licenceClass = licenceClass;
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

    public String getEmployeeNo() {
        return employeeNo;
    }

    public String getFullName() {
        return fullName;
    }

    public String getLicenceClass() {
        return licenceClass;
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
