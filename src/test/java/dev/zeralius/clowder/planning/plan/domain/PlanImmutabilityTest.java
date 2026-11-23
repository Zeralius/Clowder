package dev.zeralius.clowder.planning.plan.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zeralius.clowder.common.error.ConflictException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * The immutability rule lives in the entity rather than in a convention nobody remembers, so this is
 * a unit test: no Spring, no database, nothing to start.
 */
class PlanImmutabilityTest {

    private static final LocalDate DAY = LocalDate.of(2026, 1, 5);

    private static Plan draft() {
        return new Plan(1L, 5L, 7L, DAY, 1, 1_000L, 60);
    }

    private static PlanStop stop() {
        return new PlanStop(
                0, 42L, "REF-1", "Somewhere 1", Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, 0, 0L, 0L);
    }

    @Test
    void aNewPlanIsADraft() {
        assertThat(draft().getStatus()).isEqualTo(PlanStatus.DRAFT);
        assertThat(draft().getStops()).isEmpty();
    }

    @Test
    void aDraftTakesStops() {
        final var plan = draft();
        plan.addStop(stop());

        assertThat(plan.getStops()).hasSize(1);
    }

    @Test
    void theStopListCannotBeChangedThroughTheGetter() {
        final var plan = draft();

        assertThatThrownBy(() -> plan.getStops().add(stop())).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aPublishedPlanRefusesNewStops() {
        final var plan = draft();
        plan.publish();

        assertThatThrownBy(() -> plan.addStop(stop()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PUBLISHED");
    }

    @Test
    void aPublishedPlanCannotBePublishedAgain() {
        final var plan = draft();
        plan.publish();

        assertThatThrownBy(plan::publish).isInstanceOf(ConflictException.class);
    }

    @Test
    void aSupersededPlanIsTerminal() {
        final var plan = draft();
        plan.publish();
        plan.supersede();

        assertThatThrownBy(plan::publish).isInstanceOf(ConflictException.class);
        assertThatThrownBy(plan::supersede).isInstanceOf(ConflictException.class);
        assertThat(plan.getStatus().isTerminal()).isTrue();
    }

    @Test
    void publishingStampsTheTime() {
        final var plan = draft();
        assertThat(plan.getPublishedAt()).isNull();

        plan.publish();

        assertThat(plan.getPublishedAt()).isNotNull();
    }

    @Test
    void aDraftCanBeCancelledButNotSuperseded() {
        assertThatThrownBy(() -> draft().supersede()).isInstanceOf(ConflictException.class);

        final var plan = draft();
        plan.cancel();

        assertThat(plan.getStatus()).isEqualTo(PlanStatus.CANCELLED);
        assertThatThrownBy(() -> plan.addStop(stop())).isInstanceOf(ConflictException.class);
    }
}
