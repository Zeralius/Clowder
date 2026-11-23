package dev.zeralius.clowder.common.time;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A closed interval of absolute time, {@code [start, end]} — decision D2.
 *
 * <p>Closed rather than half-open because M2 constrains the moment service <em>starts</em>, and VRPTW
 * convention treats service beginning exactly at {@code end} as feasible. {@code overlaps} follows
 * the same reading: windows that touch at one instant do share it.
 *
 * <p>Windows are absolute instants, never times-of-day. A time-of-day window breaks the first time a
 * route crosses midnight or a DST boundary.
 */
public record TimeWindow(Instant start, Instant end) {

    public TimeWindow {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end must be after start: " + start + " .. " + end);
        }
    }

    public boolean contains(Instant instant) {
        return !instant.isBefore(start) && !instant.isAfter(end);
    }

    public boolean overlaps(TimeWindow other) {
        return !start.isAfter(other.end) && !other.start.isAfter(end);
    }

    public Duration duration() {
        return Duration.between(start, end);
    }

    public TimeWindow shiftedBy(Duration amount) {
        return new TimeWindow(start.plus(amount), end.plus(amount));
    }
}
