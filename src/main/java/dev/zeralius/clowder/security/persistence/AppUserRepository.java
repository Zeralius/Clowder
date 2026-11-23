package dev.zeralius.clowder.security.persistence;

import dev.zeralius.clowder.security.domain.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    /**
     * Whether this account's tokens are still good, read on every authenticated request.
     *
     * <p>A projection rather than {@code findByUsername}, so the hot path reads two columns and
     * cannot be tempted to change the row it loaded.
     */
    Optional<TokenValidity> findTokenValidityByUsername(String username);
}
