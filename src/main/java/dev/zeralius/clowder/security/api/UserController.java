package dev.zeralius.clowder.security.api;

import dev.zeralius.clowder.security.domain.AppUserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin only, enforced in the filter chain rather than here. */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AppUserService users;

    UserController(AppUserService users) {
        this.users = users;
    }

    @PostMapping
    ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        final var created = users.create(request);
        return ResponseEntity.created(URI.create("/api/users/" + created.id())).body(created);
    }

    @GetMapping
    List<UserResponse> findAll() {
        return users.findAll();
    }

    @GetMapping("/{id}")
    UserResponse findById(@PathVariable Long id) {
        return users.findById(id);
    }

    @PutMapping("/{id}/password")
    UserResponse changePassword(@PathVariable Long id, @Valid @RequestBody ChangePasswordRequest request) {
        return users.changePassword(id, request.password());
    }

    /**
     * Stops every token this account is holding, right now, without deactivating it.
     *
     * <p>For a lost phone, where the account is fine and the tokens are not. A deactivation would do
     * it too, but it would also take the driver off the road until somebody turned them back on.
     */
    @PostMapping("/{id}/sign-out")
    ResponseEntity<Void> signOut(@PathVariable Long id) {
        users.signOutEverywhere(id);
        return ResponseEntity.noContent().build();
    }

    /** Deactivates, like every other DELETE here (decision D3). An account is audit history. */
    @DeleteMapping("/{id}")
    ResponseEntity<Void> deactivate(@PathVariable Long id) {
        users.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
