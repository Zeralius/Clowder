package dev.zeralius.clowder.execution.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * "No UPDATE ever touches driver_event" is a claim about the code, not about a run, so it is checked
 * structurally. An entity with no way to change a field cannot be dirty, and Hibernate cannot write an
 * UPDATE for a row that never changes. A correction is a compensating event instead.
 *
 * <p>This fails the moment somebody adds a setter for convenience, which is exactly when it should.
 */
class DriverEventIsAppendOnlyTest {

    @Test
    void theEntityHasNoWayToChangeAnyField() {
        final var mutators = Arrays.stream(DriverEvent.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .filter(method -> !method.getName().startsWith("get"))
                .map(java.lang.reflect.Method::getName)
                .toList();

        assertThat(mutators).isEmpty();
    }

    @Test
    void everyFieldIsReadableSoNothingNeedsAMutatorToBeUseful() {
        final var fields = Arrays.stream(DriverEvent.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .count();

        final var getters = Arrays.stream(DriverEvent.class.getDeclaredMethods())
                .filter(method -> method.getName().startsWith("get"))
                .count();

        assertThat(getters).isEqualTo(fields);
    }
}
