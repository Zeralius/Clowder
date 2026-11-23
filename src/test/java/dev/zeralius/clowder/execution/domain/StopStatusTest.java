package dev.zeralius.clowder.execution.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The precedence table the whole out-of-order story rests on. */
class StopStatusTest {

    @Test
    void statusOnlyEverMovesUp() {
        assertThat(StopStatus.ARRIVED.outranks(StopStatus.PENDING)).isTrue();
        assertThat(StopStatus.DELIVERED.outranks(StopStatus.ARRIVED)).isTrue();
        assertThat(StopStatus.ARRIVED.outranks(StopStatus.DELIVERED)).isFalse();
        assertThat(StopStatus.PENDING.outranks(StopStatus.PENDING)).isFalse();
    }

    /** Delivered and failed share a rank, so neither can overwrite the other. */
    @Test
    void deliveredAndFailedAreEqualInRank() {
        assertThat(StopStatus.FAILED.outranks(StopStatus.DELIVERED)).isFalse();
        assertThat(StopStatus.DELIVERED.outranks(StopStatus.FAILED)).isFalse();
    }

    @Test
    void bothTerminalOutcomesCountAsComplete() {
        assertThat(StopStatus.DELIVERED.isComplete()).isTrue();
        assertThat(StopStatus.FAILED.isComplete()).isTrue();
        assertThat(StopStatus.ARRIVED.isComplete()).isFalse();
        assertThat(StopStatus.PENDING.isComplete()).isFalse();
    }

    @Test
    void routeScopedEventsBelongToNoStop() {
        assertThat(DriverEventType.DEPARTED_DEPOT.isStopScoped()).isFalse();
        assertThat(DriverEventType.RETURNED_DEPOT.isStopScoped()).isFalse();
        assertThat(DriverEventType.ARRIVED.isStopScoped()).isTrue();
    }

    @Test
    void onlyDeliveredAndFailedEndAStop() {
        assertThat(DriverEventType.DELIVERED.isTerminalForStop()).isTrue();
        assertThat(DriverEventType.FAILED.isTerminalForStop()).isTrue();
        assertThat(DriverEventType.ARRIVED.isTerminalForStop()).isFalse();
        assertThat(DriverEventType.DEPARTED.isTerminalForStop()).isFalse();
    }
}
