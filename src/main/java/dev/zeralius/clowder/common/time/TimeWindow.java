package dev.zeralius.clowder.common.time;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record TimeWindow(Instant start, Instant end) {

    public TimeWindow {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if(!end.isAfter(start)) {
            throw new IllegalArgumentException("end must be before start");
        }
    }
    public boolean contains(Instant instant) {
        return !instant.isBefore(start) && instant.isBefore(end);
    }

    public boolean overlaps(TimeWindow other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    public Duration duration() {
        return Duration.between(start, end);
    }

    public TimeWindow shiftedBy(Duration amount) {
        return new TimeWindow(start.plus(amount), end.plus(amount));
    }
}
