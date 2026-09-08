package dev.zeralius.clowder.common.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TimeWindowTest {

    private static final Instant NINE = Instant.parse("2026-01-05T09:00:00Z");
    private static final Instant TWELVE = Instant.parse("2026-01-05T12:00:00Z");

    private final TimeWindow morning = new TimeWindow(NINE, TWELVE);

    @Test
    void containsTheStartInstant() {
        assertThat(morning.contains(NINE)).isTrue();
    }

    /** Pins decision D2: the window is closed, so service starting exactly at the end is feasible. */
    @Test
    void containsTheEndInstant() {
        assertThat(morning.contains(TWELVE)).isTrue();
    }

    @Test
    void excludesInstantsOutsideTheWindow() {
        assertThat(morning.contains(NINE.minusSeconds(1))).isFalse();
        assertThat(morning.contains(TWELVE.plusSeconds(1))).isFalse();
    }

    @Test
    void windowsThatTouchAtOneInstantOverlap() {
        var afternoon = new TimeWindow(TWELVE, TWELVE.plus(Duration.ofHours(3)));

        assertThat(morning.overlaps(afternoon)).isTrue();
    }

    @Test
    void disjointWindowsDoNotOverlap() {
        var afternoon = new TimeWindow(TWELVE.plusSeconds(1), TWELVE.plus(Duration.ofHours(3)));

        assertThat(morning.overlaps(afternoon)).isFalse();
    }

    @Test
    void reportsItsDuration() {
        assertThat(morning.duration()).isEqualTo(Duration.ofHours(3));
    }

    @Test
    void shiftsBothEndsTogether() {
        var shifted = morning.shiftedBy(Duration.ofHours(2));

        assertThat(shifted.start()).isEqualTo(NINE.plus(Duration.ofHours(2)));
        assertThat(shifted.end()).isEqualTo(TWELVE.plus(Duration.ofHours(2)));
    }

    @Test
    void rejectsAnEndThatIsNotAfterTheStart() {
        assertThatThrownBy(() -> new TimeWindow(TWELVE, NINE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeWindow(NINE, NINE)).isInstanceOf(IllegalArgumentException.class);
    }
}
