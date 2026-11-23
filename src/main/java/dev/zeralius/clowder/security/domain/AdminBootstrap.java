package dev.zeralius.clowder.security.domain;

import dev.zeralius.clowder.security.persistence.AppUserRepository;
import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates one admin on a database that has no accounts at all, because a system where nobody can log
 * in needs a way in that is not "edit the table by hand".
 *
 * <p><b>It runs only when the table is empty.</b> Not "when this username is missing", which would
 * recreate a deliberately deleted admin on every restart, and not unconditionally, which would reset
 * a password somebody had changed.
 *
 * <p>If no password is configured one is generated and logged once, at WARN, with instructions. That
 * is the same bargain Spring Boot used to make for its default user: a secret that only exists in the
 * startup log of the machine that generated it is a far better default than a known one, and no
 * default at all would mean a fresh checkout cannot be run at all.
 */
@Component
class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final int GENERATED_PASSWORD_BYTES = 24;

    private final AppUserRepository users;
    private final TokenService tokens;
    private final SecurityProperties properties;

    AdminBootstrap(AppUserRepository users, TokenService tokens, SecurityProperties properties) {
        this.users = users;
        this.tokens = tokens;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }

        final var configured = properties.getBootstrapPassword();
        final var password = configured == null || configured.isBlank() ? generatePassword() : configured;

        users.save(new AppUser(properties.getBootstrapUsername(), tokens.hash(password), Role.ADMIN, null));

        if (configured == null || configured.isBlank()) {
            log.warn("""

                    ================================================================
                     No accounts existed, so an admin was created.

                       username: {}
                       password: {}

                     This password was generated, is printed here once, and cannot
                     be recovered. Change it with a PUT to /api/users/<id>/password,
                     or set clowder.security.bootstrap-password before first start.
                    ================================================================
                    """, properties.getBootstrapUsername(), password);
        } else {
            log.info(
                    "No accounts existed, so admin '{}' was created from configuration",
                    properties.getBootstrapUsername());
        }
    }

    /** URL-safe so that it survives being copied out of a terminal and into a shell. */
    private static String generatePassword() {
        final var bytes = new byte[GENERATED_PASSWORD_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
