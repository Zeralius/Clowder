package dev.zeralius.clowder.security.domain;

import dev.zeralius.clowder.common.error.ConflictException;
import dev.zeralius.clowder.common.error.NotFoundException;
import dev.zeralius.clowder.fleet.FleetFacade;
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

    public AppUserService(AppUserRepository users, TokenService tokens, FleetFacade fleet) {
        this.users = users;
        this.tokens = tokens;
        this.fleet = fleet;
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

    @Transactional
    public void deactivate(Long id) {
        users.findById(id).orElseThrow(() -> new NotFoundException("User", id)).deactivate();
    }

    @Transactional
    public UserResponse changePassword(Long id, String rawPassword) {
        final var user = users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
        user.changePasswordHash(tokens.hash(rawPassword));
        return toResponse(user);
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
