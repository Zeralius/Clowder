package dev.zeralius.clowder.planning.solver;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.common.geo.EuclideanDistanceProvider;
import dev.zeralius.clowder.common.geo.LatLon;
import dev.zeralius.clowder.planning.feasibility.RouteEvaluator;
import dev.zeralius.clowder.planning.model.DistanceMatrix;
import dev.zeralius.clowder.planning.model.ProblemInstance;
import dev.zeralius.clowder.planning.model.StopSpec;
import dev.zeralius.clowder.planning.model.VehicleSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The contract between M2 and M3: the allocation-free fast path and the evaluator never disagree. If
 * someone changes one and not the other, this fails immediately rather than showing up as mysteriously
 * worse benchmark numbers six weeks later.
 */
class RouteCalculatorAgreementTest {

    private static final int MAX_STOPS = 8;
    private static final int TRIALS = 500;
    private static final long SEED = 20260105L;

    private final RouteEvaluator evaluator = new RouteEvaluator();

    @Test
    void agreesWithTheEvaluatorOnFeasibilityAndDistance() {
        final var random = new Random(SEED);

        for (var trial = 0; trial < TRIALS; trial++) {
            final var instance = randomInstance(random);
            final var vehicle = instance.vehicles().get(0);
            final var route = randomPermutation(random, instance.stopCount());

            final var reference = evaluator.evaluate(vehicle, route, instance);
            final var fast = RouteCalculator.isFeasible(route, route.length, vehicle, instance);

            assertThat(fast)
                    .as("trial %d, route %s, violations %s", trial, Arrays.toString(route), reference.violations())
                    .isEqualTo(reference.feasible());

            assertThat(RouteCalculator.distance(route, route.length, vehicle, instance))
                    .as("distance on trial %d", trial)
                    .isEqualTo(reference.totalDistance());

            assertThat(RouteCalculator.duration(route, route.length, vehicle, instance))
                    .as("duration on trial %d", trial)
                    .isEqualTo(reference.totalDurationMinutes());
        }
    }

    /** A bug that always returns {@code false} would pass the test above; this makes sure it sees both. */
    @Test
    void theRandomFixtureProducesBothOutcomes() {
        final var random = new Random(SEED);
        var feasible = 0;
        for (var trial = 0; trial < TRIALS; trial++) {
            final var instance = randomInstance(random);
            if (RouteCalculator.isFeasible(
                    randomPermutation(random, instance.stopCount()),
                    instance.stopCount(),
                    instance.vehicles().get(0),
                    instance)) {
                feasible++;
            }
        }
        // Not merely "some of each": both branches need real coverage for the agreement test to mean anything.
        assertThat(feasible).isBetween(TRIALS / 10, TRIALS * 9 / 10);
    }

    /**
     * Route length varies from one to eight and windows are wide enough that short routes are often
     * feasible. With a fixed eight stops and narrow windows, no random permutation of 500 was ever
     * feasible, and the agreement test would have been exercising only the {@code false} branch.
     */
    private static ProblemInstance randomInstance(Random random) {
        final var stopCount = 1 + random.nextInt(MAX_STOPS);

        final var points = new ArrayList<LatLon>();
        points.add(LatLon.of(0d, 0d)); // depot at matrix index 0
        for (var i = 0; i < stopCount; i++) {
            points.add(LatLon.of(random.nextInt(80), random.nextInt(80)));
        }

        final var stops = new ArrayList<StopSpec>();
        for (var i = 0; i < stopCount; i++) {
            final var windowStart = random.nextInt(300);
            final var windowEnd = windowStart + 100 + random.nextInt(800);
            stops.add(new StopSpec(i, i + 1, random.nextInt(200), 0L, random.nextInt(20), windowStart, windowEnd));
        }

        final var matrix = DistanceMatrix.from(points, new EuclideanDistanceProvider());
        final var vehicle = new VehicleSpec(1L, 1_000L, Long.MAX_VALUE, 0, 900, 0);

        return new ProblemInstance(List.of(vehicle), stops, matrix, 0);
    }

    private static int[] randomPermutation(Random random, int size) {
        final var order = new int[size];
        for (var i = 0; i < size; i++) {
            order[i] = i;
        }
        for (var i = size - 1; i > 0; i--) {
            final var j = random.nextInt(i + 1);
            final var swap = order[i];
            order[i] = order[j];
            order[j] = swap;
        }
        return order;
    }
}
