package dev.zeralius.clowder.planning.bench;

import dev.zeralius.clowder.planning.model.ProblemInstance;
import dev.zeralius.clowder.planning.solver.HeuristicSolver;
import dev.zeralius.clowder.planning.solver.SolverConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Runs the benchmark set and writes {@code results.csv}.
 *
 * <pre>
 * java -cp target/classes dev.zeralius.clowder.planning.bench.BenchmarkMain \
 *   benchmarks/solomon-100 benchmarks/best-known.csv benchmarks/results.csv 5
 * </pre>
 *
 * <p>A {@code main} rather than a test on purpose: this takes as long as the budget times the number
 * of instances, and nothing that slow belongs in {@code mvn verify}.
 */
public final class BenchmarkMain {

    private BenchmarkMain() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            System.err.println("usage: BenchmarkMain <instanceDir> <bestKnownCsv> <resultsCsv> <budgetSeconds>");
            System.exit(2);
        }

        final var instanceDir = Path.of(args[0]);
        final var bestKnownCsv = Path.of(args[1]);
        final var resultsCsv = Path.of(args[2]);
        final var budget = Duration.ofSeconds(Long.parseLong(args[3]));

        final var registry = loadRegistry(bestKnownCsv);
        final var loader = new SolomonBenchmarkLoader();

        // Parse every instance up front: a malformed file should fail before a long run, not during.
        final var byName = new HashMap<String, ProblemInstance>();
        final var names = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(instanceDir)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".txt"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(p -> {
                        try (var reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                            final var loaded = loader.load(reader);
                            byName.put(loaded.name(), loaded.instance());
                            names.add(loaded.name());
                        } catch (IOException e) {
                            throw new UncheckedIOException("Could not read " + p, e);
                        } catch (RuntimeException e) {
                            throw new IllegalArgumentException("Could not parse " + p + ": " + e.getMessage(), e);
                        }
                    });
        }

        if (names.isEmpty()) {
            System.err.println("No .txt instances found under " + instanceDir.toAbsolutePath());
            System.exit(1);
        }

        final var missing =
                names.stream().filter(n -> registry.find(n).isEmpty()).toList();
        if (!missing.isEmpty()) {
            System.err.printf("warning: no best-known value for %d instance(s): %s%n", missing.size(), missing);
        }

        System.out.printf(Locale.ROOT, "Solving %d instance(s) with a %s budget each%n", names.size(), budget);

        final var config = SolverConfig.defaults().withBudget(budget);
        final var results = new BenchmarkRunner(new HeuristicSolver(), registry).run(names, byName::get, config);

        Files.createDirectories(resultsCsv.toAbsolutePath().getParent());
        try (var writer = Files.newBufferedWriter(resultsCsv, StandardCharsets.UTF_8)) {
            new BenchmarkCsvWriter().write(results, writer);
        }

        summarise(results, registry, resultsCsv);
    }

    private static BestKnownRegistry loadRegistry(Path csv) throws IOException {
        try (var reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            return BestKnownRegistry.load(reader);
        }
    }

    private static void summarise(
            java.util.List<BenchmarkResult> results, BestKnownRegistry registry, Path resultsCsv) {
        final var scored =
                results.stream().filter(r -> !Double.isNaN(r.gapPercent())).toList();

        System.out.printf(
                Locale.ROOT,
                "%-8s %4s %4s %10s %10s %8s %6s %7s%n",
                "instance",
                "veh",
                "best",
                "distance",
                "best",
                "gap%",
                "unas",
                "ms");
        for (var r : results) {
            System.out.printf(
                    Locale.ROOT,
                    "%-8s %4d %4s %10.2f %10s %8s %6d %7d%n",
                    r.instance(),
                    r.vehicles(),
                    r.bestVehicles() > 0 ? String.valueOf(r.bestVehicles()) : "-",
                    r.distance(),
                    Double.isNaN(r.bestDistance()) ? "-" : String.format(Locale.ROOT, "%.2f", r.bestDistance()),
                    Double.isNaN(r.gapPercent()) ? "-" : String.format(Locale.ROOT, "%.2f", r.gapPercent()),
                    r.unassigned(),
                    r.millis());
        }

        if (!scored.isEmpty()) {
            final var meanGap = scored.stream()
                    .mapToDouble(BenchmarkResult::gapPercent)
                    .average()
                    .orElse(Double.NaN);
            final var excess =
                    scored.stream().mapToInt(BenchmarkResult::excessVehicles).sum();
            final var unassigned =
                    results.stream().mapToInt(BenchmarkResult::unassigned).sum();
            System.out.printf(
                    Locale.ROOT,
                    "%n%d of %d instances scored against %d known references.%n"
                            + "mean distance gap %.2f%%, %d vehicle(s) over best-known in total, %d consignment(s) unrouted.%n",
                    scored.size(),
                    results.size(),
                    registry.size(),
                    meanGap,
                    excess,
                    unassigned);
        }
        System.out.println("wrote " + resultsCsv.toAbsolutePath());
    }
}
