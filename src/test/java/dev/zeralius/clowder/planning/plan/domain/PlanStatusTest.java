package dev.zeralius.clowder.planning.plan.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Exhaustive over all sixteen pairs, same as {@code ConsignmentStatusTest}. A transition table is
 * cheap to get subtly wrong and cheap to pin completely, so it gets pinned completely.
 */
class PlanStatusTest {

    private static final Set<String> LEGAL = Set.of("DRAFT->PUBLISHED", "DRAFT->CANCELLED", "PUBLISHED->SUPERSEDED");

    @ParameterizedTest
    @EnumSource(PlanStatus.class)
    void everyPairIsExactlyAsTheTableSays(PlanStatus from) {
        for (final var to : PlanStatus.values()) {
            final var legal = LEGAL.contains(from + "->" + to);
            assertThat(from.canTransitionTo(to)).as("%s to %s", from, to).isEqualTo(legal);
        }
    }

    @Test
    void aPublishedPlanCanOnlyBeSuperseded() {
        assertThat(PlanStatus.PUBLISHED.canTransitionTo(PlanStatus.SUPERSEDED)).isTrue();
        assertThat(PlanStatus.PUBLISHED.canTransitionTo(PlanStatus.DRAFT)).isFalse();
        assertThat(PlanStatus.PUBLISHED.canTransitionTo(PlanStatus.CANCELLED)).isFalse();
    }

    @Test
    void supersededAndCancelledAreTerminal() {
        assertThat(PlanStatus.SUPERSEDED.isTerminal()).isTrue();
        assertThat(PlanStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(PlanStatus.DRAFT.isTerminal()).isFalse();
        assertThat(PlanStatus.PUBLISHED.isTerminal()).isFalse();
    }
}
