package dev.zeralius.clowder.security.domain;

import dev.zeralius.clowder.common.error.ConflictException;
import dev.zeralius.clowder.common.error.NotFoundException;
import dev.zeralius.clowder.fleet.FleetFacade;
import dev.zeralius.clowder.security.CurrentUser;
import dev.zeralius.clowder.security.api.CreateUserRequest;
import dev.zeralius.clowder.security.api.UserResponse;
import dev.zeralius.clowder.security.persistence.AppUserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Accounts, created by an admin. There is no self-registration: this is a fleet, not a forum. */
@Service
@Transactional(readOnly = true)
public class AppUserService {

    private final AppUserRepository users;
    private final TokenService tokens;
    private final FleetFacade fleet;
    private final CurrentUser currentUser;

    public AppUserService(AppUserRepository users, TokenService tokens, FleetFacade fleet, CurrentUser currentUser) {
        this.users = users;
        this.tokens = tokens;
        this.fleet = fleet;
        this.currentUser = currentUser;
    }

    public List<UserResponse> findAll() {
        return users.findAll().stream().map(AppUserService::toResponse).toList();
    }

    public UserResponse findById(Long id) {
        return users.findById(id).map(AppUserService::toResponse).orElseThrow(() -> new NotFoundException("User", id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        // Racy on purpose, like the vehicle registration check: this is for the error message and the
        // unique constraint is the guarantee.
        if (users.existsByUsername(request.username())) {
            throw new ConflictException("A user named '%s' already exists".formatted(request.username()));
        }

        if (request.role() == Role.DRIVER) {
            requireDriverExists(request.driverId());
        }

        final var user = new AppUser(
                request.username(),
                tokens.hash(request.password()),
                request.role(),
                request.role() == Role.DRIVER ? request.driverId() : null);

        return toResponse(users.save(user));
    }

    /**
     * Deactivating also revokes, and both are needed.
     *
     * <p>The deactivation is what stops the account now: every request re-reads whether it is active,
     * so the token in a leaver's pocket dies on their next call. The revocation is about later —
     * without it, reactivating the account would bring every token issued before the deactivation
     * back to life, including the one that was taken away from them.
     */
    @Transactional
    public void deactivate(Long id) {
        final var user = users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
        user.deactivate();
        user.revokeIssuedTokens();
    }

    /**
     * Changing a password signs the account out everywhere.
     *
     * <p>A password is changed either because it leaked or because somebody is tidying up, and only
     * one of those is the common case worth optimising for. Leaving the old tokens alive would mean a
     * leaked password stays useful for eight hours after it was replaced, which makes the reset
     * theatre. The cost is that a user changing their own password has to sign in again on their
     * other devices, which is also what they would expect.
     */
    @Transactional
    public UserResponse changePassword(Long id, String rawPassword) {
        final var user = users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
        user.changePasswordHash(tokens.hash(rawPassword));
        user.revokeIssuedTokens();
        return toResponse(user);
    }

    /** Stops every token this account holds, without touching whether it may sign in again. */
    @Transactional
    public void signOutEverywhere(Long id) {
        users.findById(id).orElseThrow(() -> new NotFoundException("User", id)).revokeIssuedTokens();
    }

    /**
     * The caller signing themselves out of every device, the one they are holding included.
     *
     * <p>Here rather than only on the admin endpoint because the person who notices a phone is
     * missing is the person who lost it, usually before anyone with an admin account is awake. They
     * need a second device and their password, not a support call.
     */
    @Transactional
    public void signOutCaller() {
        final var username = currentUser.require().username();
        users.findByUsername(username)
                // Unreachable: the token that authenticated this request was only accepted because
                // the account behind it exists. Not a client error if it ever happens.
                .orElseThrow(
                        () -> new ConflictException("The authenticated caller '%s' has no account".formatted(username)))
                .revokeIssuedTokens();
    }

    /**
     * A driver account pointing at a driver that does not exist would authenticate happily and then
     * own nothing, which is a confusing way to be locked out. The facade is how fleet is asked.
     */
    private void requireDriverExists(Long driverId) {
        if (driverId == null) {
            throw new ConflictException("A driver account must name the driver it belongs to");
        }
        final var known = fleet.findAllDepots().stream()
                .flatMap(depot -> fleet.findActiveDriversAt(depot.id()).stream())
                .anyMatch(driver -> driver.id().equals(driverId));
        if (!known) {
            throw new NotFoundException("Active driver", driverId);
        }
    }

    /** No password, no hash, not even a length. A response that mentions either is a response too many. */
    private static UserResponse toResponse(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.getDriverId(), user.isActive());
    }
}
