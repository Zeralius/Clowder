package dev.zeralius.clowder.planning.bench;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.planning.model.StopSpec;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class SolomonBenchmarkLoaderTest {

    private static Reader fixture() {
        var stream = Objects.requireNonNull(
                SolomonBenchmarkLoaderTest.class.getResourceAsStream("/solomon/MINI.txt"), "fixture missing");
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }

    @Test
    void buildsAProblemInstanceWithoutADatabase() {
        var loaded = new SolomonBenchmarkLoader().load(fixture());

        assertThat(loaded.name()).isEqualTo("MINI");
        assertThat(loaded.instance().stopCount()).isEqualTo(3);
        assertThat(loaded.instance().vehicles()).hasSize(3);
        assertThat(loaded.points()).hasSize(4); // depot plus three customers
    }

    /** Every vehicle gets the depot's due date as its shift, which is what the horizon means. */
    @Test
    void givesEveryVehicleTheHorizonAsItsShift() {
        var loaded = new SolomonBenchmarkLoader().load(fixture());

        assertThat(loaded.instance().vehicles()).allSatisfy(v -> {
            assertThat(v.shiftMinutes()).isEqualTo(230);
            assertThat(v.shiftStartMinutes()).isZero();
            assertThat(v.capacityGrams()).isEqualTo(200L);
        });
    }

    /** Windows come straight across as minutes; customer 1 is ready at 161, due at 171. */
    @Test
    void carriesWindowsAcrossAsRawMinutes() {
        var loaded = new SolomonBenchmarkLoader().load(fixture());

        assertThat(loaded.instance().stops())
                .extracting(StopSpec::windowStartMinutes, StopSpec::windowEndMinutes)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(161, 171),
                        org.assertj.core.groups.Tuple.tuple(50, 60),
                        org.assertj.core.groups.Tuple.tuple(116, 126));
    }

    /** x is longitude and y is latitude — the depot of MINI sits at (35, 35). */
    @Test
    void mapsXToLongitudeAndYToLatitude() {
        var depot = new SolomonBenchmarkLoader().load(fixture()).points().get(0);

        assertThat(depot.longitude()).isEqualByComparingTo("35");
        assertThat(depot.latitude()).isEqualByComparingTo("35");
    }
}
