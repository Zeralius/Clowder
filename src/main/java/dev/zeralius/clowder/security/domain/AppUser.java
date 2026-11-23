package dev.zeralius.clowder.security.domain;

import dev.zeralius.clowder.common.error.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * An account.
 *
 * <p>{@code driverId} is a plain {@code Long}, not a {@code @ManyToOne Driver}: fleet is another
 * module and the boundary rule has not changed since M1. It is also what answers the only
 * interesting authorisation question in this system — whether the person reporting a delivery is the
 * driver the plan was given to.
 *
 * <p>The password is stored as a bcrypt hash and nothing here can read it back. There is no getter
 * for it beyond what the authentication path needs, and no mutator that takes a plaintext string:
 * hashing belongs to the service that owns the encoder, so a caller cannot accidentally store one.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    /** The driver this account is. Null unless the role is {@link Role#DRIVER}. */
    @Column(name = "driver_id")
    private Long driverId;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    /** For JPA. */
    protected AppUser() {}

    /**
     * @param passwordHash already encoded; this constructor will not hash for you, because a
     *     constructor that quietly accepts either a hash or a plaintext is how a plaintext gets stored
     */
    public AppUser(String username, String passwordHash, Role role, Long driverId) {
        if (role == Role.DRIVER && driverId == null) {
            throw new ConflictException("A driver account must be linked to a driver");
        }
        if (role != Role.DRIVER && driverId != null) {
            throw new ConflictException("Only a driver account may be linked to a driver");
        }
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.driverId = driverId;
        this.active = true;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
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

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public Long getDriverId() {
        return driverId;
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
