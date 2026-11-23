package dev.zeralius.clowder.planning.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BestKnownRegistryTest {

    /** Instance files are named in lower case and name themselves in upper; either must resolve. */
    @Test
    void matchesRegardlessOfCase() {
        var registry = BestKnownRegistry.load(new StringReader("instance,vehicles,distance\nRC101,14,1696.95\n"));

        assertThat(registry.find("rc101")).isPresent();
        assertThat(registry.find("RC101")).isPresent();
        assertThat(registry.find(" Rc101 ")).isPresent();
        assertThat(registry.find("RC999")).isEmpty();
    }

    @Test
    void skipsBlankAndCommentedLines() {
        var registry =
                BestKnownRegistry.load(new StringReader("instance,vehicles,distance\n\n# a note\nC101,10,828.94\n"));

        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void rejectsATruncatedRow() {
        assertThatThrownBy(() -> BestKnownRegistry.load(new StringReader("instance,vehicles,distance\nC101,10\n")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3 columns");
    }

    @Test
    void rejectsAnEmptyFile() {
        assertThatThrownBy(() -> BestKnownRegistry.load(new StringReader("")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** The committed file is the one the benchmark run reads; a typo in it is a silently wrong table. */
    @Test
    void theCommittedFileHasAllFiftySixSolomonInstances() throws IOException {
        var csv = Path.of("benchmarks/best-known.csv");
        assertThat(csv).exists();

        try (var reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            var registry = BestKnownRegistry.load(reader);

            assertThat(registry.size()).isEqualTo(56);
            // Spot-checks against the three values quoted independently in the M3 guide.
            assertThat(registry.find("C101"))
                    .get()
                    .extracting(BestKnownRegistry.BestKnown::distance)
                    .isEqualTo(828.94);
            assertThat(registry.find("R101"))
                    .get()
                    .extracting(BestKnownRegistry.BestKnown::vehicles)
                    .isEqualTo(19);
            assertThat(registry.find("RC101"))
                    .get()
                    .extracting(BestKnownRegistry.BestKnown::distance)
                    .isEqualTo(1696.95);
        }
    }
}
