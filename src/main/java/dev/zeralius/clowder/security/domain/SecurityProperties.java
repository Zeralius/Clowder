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

    private final RateLimit rateLimit = new RateLimit();

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

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    /**
     * How much password guessing the token endpoint tolerates. The defaults are meant to be left
     * alone; they exist as settings so that an operator who needs to loosen them at three in the
     * morning does not need a release.
     */
    public static class RateLimit {

        /**
         * Failures allowed for one username from one address before it is refused. Per address on
         * purpose, so that nobody can lock a dispatcher out of their shift from somewhere else.
         */
        private int maxFailuresPerUser = 5;

        /** Failures allowed from one address across all usernames. This is what catches spraying. */
        private int maxFailuresPerAddress = 20;

        /** How long a failure counts for. The retry hint is measured from the oldest one still inside it. */
        private Duration window = Duration.ofMinutes(15);

        /**
         * Keys the limiter will hold before evicting the least recently used. Bounds the memory an
         * attacker cycling usernames can make it spend; the eviction order means their own key is the
         * last thing to go.
         */
        private int maxTrackedKeys = 10_000;

        public int getMaxFailuresPerUser() {
            return maxFailuresPerUser;
        }

        public void setMaxFailuresPerUser(int maxFailuresPerUser) {
            this.maxFailuresPerUser = maxFailuresPerUser;
        }

        public int getMaxFailuresPerAddress() {
            return maxFailuresPerAddress;
        }

        public void setMaxFailuresPerAddress(int maxFailuresPerAddress) {
            this.maxFailuresPerAddress = maxFailuresPerAddress;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }

        public int getMaxTrackedKeys() {
            return maxTrackedKeys;
        }

        public void setMaxTrackedKeys(int maxTrackedKeys) {
            this.maxTrackedKeys = maxTrackedKeys;
        }
    }
}
