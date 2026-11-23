package dev.zeralius.clowder.security.domain;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token signing and the first-run account.
 *
 * <p><b>There is no default signing secret.</b> A framework that ships one is a framework where
 * every deployment that forgot to set it can have its tokens forged by anyone who read the source.
 * If this is unset the application refuses to start and says so, which is a worse first five minutes
 * and a much better production.
 *
 * <p>The bootstrap password has no default either. Leave it unset and the first start generates one,
 * logs it once at WARN, and tells you to change it — the same bargain Spring Boot used to make for
 * its default user, and for the same reason.
 */
@ConfigurationProperties(prefix = "clowder.security")
public class SecurityProperties {

    /** HMAC signing secret, at least 32 bytes. No default, on purpose. */
    private String jwtSecret;

    private Duration tokenTtl = Duration.ofHours(8);

    /** Username of the account created on first start when there are no users at all. */
    private String bootstrapUsername = "admin";

    /** Password for that account. Unset means generate one and log it once. */
    private String bootstrapPassword;

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public Duration getTokenTtl() {
        return tokenTtl;
    }

    public void setTokenTtl(Duration tokenTtl) {
        this.tokenTtl = tokenTtl;
    }

    public String getBootstrapUsername() {
        return bootstrapUsername;
    }

    public void setBootstrapUsername(String bootstrapUsername) {
        this.bootstrapUsername = bootstrapUsername;
    }

    public String getBootstrapPassword() {
        return bootstrapPassword;
    }

    public void setBootstrapPassword(String bootstrapPassword) {
        this.bootstrapPassword = bootstrapPassword;
    }
}
