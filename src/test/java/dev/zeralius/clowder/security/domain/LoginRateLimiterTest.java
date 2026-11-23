package dev.zeralius.clowder.security.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zeralius.clowder.common.error.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The budgets, with the clock under the test's control: a limiter tested by sleeping is a limiter
 * tested at one window length, and that length is fifteen minutes.
 *
 * <p>No Spring and no database. The rule is arithmetic over a map, so this is where it belongs.
 */
class LoginRateLimiterTest {

    private static final String THEIR_DESK = "203.0.113.7";
    private static final String SOMEWHERE_ELSE = "198.51.100.4";
    private static final String USER = "dolores";

    private MutableClock clock;
    private LoginRateLimiter limiter;
    private Duration window;
    private int perUser;
    private int perAddress;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-05T06:00:00Z"));

        // The shipped defaults, not numbers chosen to make the test short. If a default changes, these
        // tests change with it and the change is visible in the diff.
        final var properties = new SecurityProperties();
        window = properties.getRateLimit().getWindow();
        perUser = properties.getRateLimit().getMaxFailuresPerUser();
        perAddress = properties.getRateLimit().getMaxFailuresPerAddress();

        limiter = new LoginRateLimiter(clock, properties);
    }

    private void fail(String username, String address, int times) {
        for (var i = 0; i < times; i++) {
            limiter.recordFailure(username, address);
        }
    }

    @Test
    void failuresUpToTheBudgetAreAllowedThroughAndTheNextIsNot() {
        fail(USER, THEIR_DESK, perUser - 1);
        assertThatCode(() -> limiter.requireAllowed(USER, THEIR_DESK)).doesNotThrowAnyException();

        limiter.recordFailure(USER, THEIR_DESK);

        assertThatThrownBy(() -> limiter.requireAllowed(USER, THEIR_DESK))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Too many failed sign-in attempts");
    }

    /**
     * The decision this whole class turns on. A username-only budget would let anyone who knows a
     * dispatcher works here take them off the air for fifteen minutes with five wrong passwords, and
     * the lockout would be the attack rather than the defence.
     */
    @Test
    void oneAddressCannotLockAUserOutOfAnother() {
        fail(USER, THEIR_DESK, perUser);

        assertThatThrownBy(() -> limiter.requireAllowed(USER, THEIR_DESK)).isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> limiter.requireAllowed(USER, SOMEWHERE_ELSE)).doesNotThrowAnyException();
    }

    /** Spraying: one address, a fresh username every five guesses. The address budget is what bites. */
    @Test
    void manyUsernamesFromOneAddressRunOutOfTheAddressBudget() {
        for (var i = 0; i < perAddress / perUser; i++) {
            fail("candidate-" + i, THEIR_DESK, perUser);
        }

        assertThatThrownBy(() -> limiter.requireAllowed("someone-not-tried-yet", THEIR_DESK))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> limiter.requireAllowed("someone-not-tried-yet", SOMEWHERE_ELSE))
                .doesNotThrowAnyException();
    }

    @Test
    void aSuccessfulSignInForgetsThatUsersFailures() {
        fail(USER, THEIR_DESK, perUser - 1);
        limiter.recordSuccess(USER, THEIR_DESK);

        fail(USER, THEIR_DESK, perUser - 1);

        assertThatCode(() -> limiter.requireAllowed(USER, THEIR_DESK)).doesNotThrowAnyException();
    }

    /**
     * And does not refund the address budget. Otherwise an attacker who holds one valid account —
     * their own, for instance — wipes their spray allowance whenever they like.
     */
    @Test
    void aSuccessfulSignInDoesNotRefundTheAddressBudget() {
        for (var i = 0; i < perAddress / perUser; i++) {
            fail("candidate-" + i, THEIR_DESK, perUser);
        }
        limiter.recordSuccess(USER, THEIR_DESK);

        assertThatThrownBy(() -> limiter.requireAllowed(USER, THEIR_DESK)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void failuresExpireWithTheWindow() {
        fail(USER, THEIR_DESK, perUser);
        clock.advance(window.plusSeconds(1));

        assertThatCode(() -> limiter.requireAllowed(USER, THEIR_DESK)).doesNotThrowAnyException();
    }

    /**
     * A sliding window, so the hint is measured from the oldest failure still counting rather than
     * from the end of some fixed bucket. Five minutes in, ten minutes left.
     */
    @Test
    void theRetryHintSaysWhenTheOldestFailureExpires() {
        fail(USER, THEIR_DESK, perUser);
        clock.advance(Duration.ofMinutes(5));

        assertThat(retryHintFor(USER, THEIR_DESK)).isEqualTo(window.minusMinutes(5));
    }

    private Duration retryHintFor(String username, String address) {
        try {
            limiter.requireAllowed(username, address);
            throw new AssertionError("expected the limiter to refuse " + username);
        } catch (TooManyRequestsException refused) {
            return refused.getRetryAfter();
        }
    }

    /**
     * Partial expiry lets one more attempt through rather than all five, which is the difference
     * between a sliding window and a counter that resets.
     */
    @Test
    void theOldestFailureExpiringBuysExactlyOneMoreAttempt() {
        limiter.recordFailure(USER, THEIR_DESK);
        clock.advance(Duration.ofMinutes(1));
        fail(USER, THEIR_DESK, perUser - 1);

        assertThatThrownBy(() -> limiter.requireAllowed(USER, THEIR_DESK)).isInstanceOf(TooManyRequestsException.class);

        clock.advance(window.minusMinutes(1).plusSeconds(1));
        assertThatCode(() -> limiter.requireAllowed(USER, THEIR_DESK)).doesNotThrowAnyException();

        limiter.recordFailure(USER, THEIR_DESK);
        assertThatThrownBy(() -> limiter.requireAllowed(USER, THEIR_DESK)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void clearForgetsEverything() {
        fail(USER, THEIR_DESK, perUser);
        limiter.clear();

        assertThatCode(() -> limiter.requireAllowed(USER, THEIR_DESK)).doesNotThrowAnyException();
    }

    /**
     * An attacker cycling usernames cannot grow the map past the cap, and the key that keeps them
     * refused is the most recently used one, so it is the last thing eviction would take.
     */
    @Test
    void theTrackedKeysAreBounded() {
        final var properties = new SecurityProperties();
        properties.getRateLimit().setMaxTrackedKeys(8);
        final var bounded = new LoginRateLimiter(clock, properties);

        for (var i = 0; i < 1_000; i++) {
            bounded.recordFailure("throwaway-" + i, THEIR_DESK);
        }

        assertThatThrownBy(() -> bounded.requireAllowed("throwaway-999", THEIR_DESK))
                .as("the address budget survives the eviction that the username keys do not")
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void sanityCheckTheDefaultsThisTestAssumes() {
        assertThat(perUser).isPositive();
        assertThat(perAddress).isGreaterThanOrEqualTo(perUser);
        assertThat(perAddress % perUser)
                .as("the spraying tests divide one budget by the other")
                .isZero();
    }

    /** A clock a test can wind forward, so the window can be fifteen minutes and the test instant. */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException("not needed");
        }
    }
}
