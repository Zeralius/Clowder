package dev.zeralius.clowder.planning.bench;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Instance name to published best-known (vehicles, distance). Insertion-ordered for stable output. */
public final class BestKnownRegistry {

    /** Published reference values, in whole units — not the solver's minor units. */
    public record BestKnown(String instance, int vehicles, double distance) {}

    private final Map<String, BestKnown> byInstance;

    private BestKnownRegistry(Map<String, BestKnown> byInstance) {
        this.byInstance = byInstance;
    }

    public static BestKnownRegistry load(Reader reader) {
        final var byInstance = new LinkedHashMap<String, BestKnown>();

        try (var buffered = new BufferedReader(reader)) {
            var line = buffered.readLine(); // header
            if (line == null) {
                throw new IllegalArgumentException("best-known file is empty");
            }
            while ((line = buffered.readLine()) != null) {
                final var trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                final var fields = trimmed.split(",");
                if (fields.length < 3) {
                    throw new IllegalArgumentException("Expected 3 columns, got: " + trimmed);
                }
                final var instance = fields[0].trim();
                byInstance.put(
                        key(instance),
                        new BestKnown(
                                instance,
                                Integer.parseInt(fields[1].trim()),
                                Double.parseDouble(fields[2].trim())));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read best-known values", e);
        }

        return new BestKnownRegistry(byInstance);
    }

    /** Instance files are named in lower case and name themselves in upper; neither should decide a match. */
    public Optional<BestKnown> find(String instance) {
        return Optional.ofNullable(byInstance.get(key(instance)));
    }

    public int size() {
        return byInstance.size();
    }

    private static String key(String instance) {
        return instance.trim().toUpperCase(Locale.ROOT);
    }
}
