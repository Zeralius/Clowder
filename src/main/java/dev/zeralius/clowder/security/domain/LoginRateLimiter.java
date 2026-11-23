package dev.zeralius.clowder.security.domain;

import dev.zeralius.clowder.common.error.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Slows down password guessing at the token endpoint.
 *
 * <p>Two budgets, both counting failures only and both refusing before any bcrypt work is done —
 * spending a hash on a request already over budget is the cost this is meant to avoid.
 *
 * <p><b>The per-user budget is keyed on the username <i>and</i> the caller address, not on the
 * username alone, and that is the central decision here.</b> A username-only budget means anyone who
 * knows a dispatcher signs in here can lock them out of their shift by posting five wrong passwords,
 * which costs the attacker nothing and takes the depot off the air: the lockout becomes the attack.
 * Keyed per address, a wrong guess from somewhere else cannot touch the real user, while guessing
 * from any one source is still capped. What that does not cover is one account guessed from many
 * addresses at once, which needs a botnet and is bounded by the twelve-character password floor; the
 * README says so rather than implying otherwise.
 *
 * <p>The per-address budget is the one that catches spraying: many usernames tried from one place.
 * It is not cleared by a successful sign-in, or an attacker holding one valid account could reset
 * their own allowance at will.
 *
 * <p><b>In memory, per instance.</b> Two instances mean two budgets and therefore twice the
 * allowance, so this is real protection for one instance and a proportionate reduction for several.
 * Shared counters need shared state — Redis, a table, or doing this at the reverse proxy — and that
 * is a different change. The map is bounded and evicts least-recently-used keys, so an attacker
 * cycling usernames cannot grow it: the keys that fall out are the quiet ones whose counts were
 * about to expire anyway, and the attacker's own key stays hot and keeps biting.
 */
@Service
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    /**
     * Not a character a username or an address contains, so "u\nalice\n10.0.0.1" cannot be spelled by
     * any other pair.
     */
    private static final String SEPARATOR = "\n";

    private final Clock clock;
    private final SecurityProperties.RateLimit limits;

    /**
     * Guarded by its own monitor. One lock rather than striping: every operation here is a handful of
     * map and deque calls next to a bcrypt hash that costs tens of milliseconds, and the endpoint is
     * one request per sign-in.
     */
    private final Map<String, Deque<Instant>> failures;

    LoginRateLimiter(Clock clock, SecurityProperties properties) {
        this.clock = clock;
        this.limits = properties.getRateLimit();

        final var capacity = limits.getMaxTrackedKeys();
        this.failures = new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> eldest) {
                return size() > capacity;
            }
        };
    }

    /**
     * @throws TooManyRequestsException when either budget for this caller is exhausted
     */
    public void requireAllowed(String username, String callerAddress) {
        final var now = clock.instant();
        synchronized (failures) {
            refuseIfSpent(userKey(username, callerAddress), limits.getMaxFailuresPerUser(), now, callerAddress);
            refuseIfSpent(addressKey(callerAddress), limits.getMaxFailuresPerAddress(), now, callerAddress);
        }
    }

    /**
     * One failed attempt, whether or not the username exists.
     *
     * <p>Counting an unknown username exactly like a wrong password is the same rule the single
     * failure message follows: if the limiter treated them differently, how quickly the endpoint
     * started refusing would answer the question the message refuses to.
     *
     * <p>Deliberately not transactional. The attempt happened; a rolled back read-only transaction
     * around it does not make it unhappen.
     */
    public void recordFailure(String username, String callerAddress) {
        final var now = clock.instant();
        synchronized (failures) {
            append(userKey(username, callerAddress), now, limits.getMaxFailuresPerUser());
            append(addressKey(callerAddress), now, limits.getMaxFailuresPerAddress());
        }
    }

    /** A user who finally remembers their password starts again from zero. */
    public void recordSuccess(String username, String callerAddress) {
        synchronized (failures) {
            failures.remove(userKey(username, callerAddress));
            // The address count stays. See the class comment: clearing it would hand an attacker with
            // one valid account a way to wipe their own spray budget.
        }
    }

    /**
     * Forget every count. The operational lever for an office locked out by a misconfigured client,
     * and what lets a test start from a known state.
     */
    public void clear() {
        synchronized (failures) {
            failures.clear();
        }
    }

    private void refuseIfSpent(String key, int limit, Instant now, String callerAddress) {
        final var recent = failures.get(key);
        if (recent == null) {
            return;
        }
        discardExpired(recent, now);
        if (recent.size() < limit) {
            return;
        }

        final var retryAfter = Duration.between(now, recent.peekFirst().plus(limits.getWindow()));

        // The address, not the username: people type passwords into the username field, and this
        // would be the line that wrote one to the log.
        log.warn(
                "Refusing sign-in attempts from {}: {} failures within {}",
                callerAddress,
                recent.size(),
                limits.getWindow());

        throw new TooManyRequestsException(
                "Too many failed sign-in attempts. Try again in %d seconds"
                        .formatted(Math.max(1, retryAfter.toSeconds())),
                retryAfter);
    }

    private void append(String key, Instant now, int limit) {
        final var recent = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>(limit + 1));
        discardExpired(recent, now);
        recent.addLast(now);
        while (recent.size() > limit) {
            // Nothing beyond the limit can change an answer, so the deque never grows past it.
            recent.pollFirst();
        }
    }

    /**
     * A sliding window rather than a fixed one: a count that reset on the hour would let an attacker
     * spend two budgets back to back across the boundary, and the retry hint would be a guess.
     *
     * <p>An emptied deque is left in place for the eviction policy to deal with. Removing the key here
     * would be the only thing in this class that can detach a deque another caller is holding.
     */
    private void discardExpired(Deque<Instant> recent, Instant now) {
        final var cutoff = now.minus(limits.getWindow());
        while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
            recent.pollFirst();
        }
    }

    private static String userKey(String username, String callerAddress) {
        return "u" + SEPARATOR + username + SEPARATOR + callerAddress;
    }

    private static String addressKey(String callerAddress) {
        return "a" + SEPARATOR + callerAddress;
    }
}
