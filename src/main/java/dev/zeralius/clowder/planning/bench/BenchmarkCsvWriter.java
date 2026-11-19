package dev.zeralius.clowder.planning.bench;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;
import java.util.Locale;

/**
 * Writes {@code benchmarks/results.csv}. Commit the output; do not regenerate it in CI.
 *
 * <p>{@code Locale.ROOT} is not a detail. On a German JVM {@code String.format("%.2f", 1650.8)}
 * produces {@code 1650,80} and every row gains a spurious column — the single most likely way this
 * file breaks on one machine and not another.
 */
public final class BenchmarkCsvWriter {

    private static final String HEADER =
            "instance,vehicles,bestVehicles,distance,bestDistance,gapPercent,unassigned,millis";

    public void write(List<BenchmarkResult> results, Writer writer) {
        try {
            writer.write(HEADER);
            writer.write('\n');
            for (var r : results) {
                writer.write(String.format(
                        Locale.ROOT,
                        "%s,%d,%d,%.2f,%s,%s,%d,%d%n",
                        r.instance(),
                        r.vehicles(),
                        r.bestVehicles(),
                        r.distance(),
                        number(r.bestDistance()),
                        number(r.gapPercent()),
                        r.unassigned(),
                        r.millis()));
            }
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write benchmark results", e);
        }
    }

    /** An unknown reference is an empty cell, never the literal {@code NaN}. */
    private static String number(double value) {
        return Double.isNaN(value) ? "" : String.format(Locale.ROOT, "%.2f", value);
    }
}
