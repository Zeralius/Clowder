package dev.zeralius.clowder.consignment.domain;

import static dev.zeralius.clowder.consignment.domain.ConsignmentStatus.CANCELLED;
import static dev.zeralius.clowder.consignment.domain.ConsignmentStatus.DELIVERED;
import static dev.zeralius.clowder.consignment.domain.ConsignmentStatus.DISPATCHED;
import static dev.zeralius.clowder.consignment.domain.ConsignmentStatus.FAILED;
import static dev.zeralius.clowder.consignment.domain.ConsignmentStatus.PENDING;
import static dev.zeralius.clowder.consignment.domain.ConsignmentStatus.PLANNED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Exhaustive on purpose: a pure function over a six-element enum is thirty-six pairs, and checking
 * every one means the transition table can never drift from what the tests believe it is.
 */
class ConsignmentStatusTest {

    private static final Map<ConsignmentStatus, Set<ConsignmentStatus>> EXPECTED = Map.of(
            PENDING, Set.of(PLANNED, CANCELLED),
            PLANNED, Set.of(DISPATCHED, PENDING, CANCELLED),
            DISPATCHED, Set.of(DELIVERED, FAILED),
            DELIVERED, Set.of(),
            FAILED, Set.of(PENDING),
            CANCELLED, Set.of());

    @ParameterizedTest
    @EnumSource(ConsignmentStatus.class)
    void allowsExactlyTheDocumentedTransitions(ConsignmentStatus from) {
        for (var to : ConsignmentStatus.values()) {
            assertThat(from.canTransitionTo(to))
                    .as("%s -> %s", from, to)
                    .isEqualTo(EXPECTED.get(from).contains(to));
        }
    }

    @Test
    void deliveredAndCancelledAreTerminal() {
        assertThat(DELIVERED.isTerminal()).isTrue();
        assertThat(CANCELLED.isTerminal()).isTrue();
    }

    @Test
    void aPlannedConsignmentCanBeUnplanned() {
        assertThat(PLANNED.canTransitionTo(PENDING)).isTrue();
    }

    @Test
    void aFailedConsignmentCanBeRetried() {
        assertThat(FAILED.canTransitionTo(PENDING)).isTrue();
    }

    @Test
    void nothingEscapesADeliveredConsignment() {
        assertThat(DELIVERED.allowedTargets()).isEmpty();
    }
}
