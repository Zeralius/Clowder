package dev.zeralius.clowder.planning.bench;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BenchmarkCsvWriterTest {

    private final Locale original = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    /**
     * The regression this class exists for: on a German JVM a default-locale {@code %.2f} writes
     * {@code 1650,80} and every row of the CSV gains a column.
     */
    @Test
    void writesADecimalPointEvenOnAGermanJvm() {
        Locale.setDefault(Locale.GERMANY);
        var out = new StringWriter();

        new BenchmarkCsvWriter()
                .write(List.of(BenchmarkResult.of("R101", 20, 1750.5, 0, 19, 1650.80, 42)), out);

        var row = out.toString().lines().skip(1).findFirst().orElseThrow();
        assertThat(row).isEqualTo("R101,20,19,1750.50,1650.80,6.04,0,42");
        assertThat(row.split(",")).hasSize(8);
    }

    /** An unknown reference writes empty cells, not the literal NaN. */
    @Test
    void leavesUnknownReferencesBlank() {
        var out = new StringWriter();

        new BenchmarkCsvWriter()
                .write(List.of(BenchmarkResult.of("MINI", 2, 120.0, 1, 0, Double.NaN, 7)), out);

        assertThat(out.toString().lines().skip(1).findFirst().orElseThrow())
                .isEqualTo("MINI,2,0,120.00,,,1,7");
    }

    @Test
    void roundTripsThroughTheRegistry() {
        var registry = BestKnownRegistry.load(new StringReader("instance,vehicles,distance\nR101,19,1650.80\n"));

        assertThat(registry.find("R101")).get().isEqualTo(new BestKnownRegistry.BestKnown("R101", 19, 1650.80));
    }
}
