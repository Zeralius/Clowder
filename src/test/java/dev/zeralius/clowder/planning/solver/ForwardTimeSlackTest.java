package dev.zeralius.clowder.planning.solver;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.common.geo.EuclideanDistanceProvider;
import dev.zeralius.clowder.common.geo.LatLon;
import dev.zeralius.clowder.planning.model.DistanceMatrix;
import dev.zeralius.clowder.planning.model.ProblemInstance;
import dev.zeralius.clowder.planning.model.StopSpec;
import dev.zeralius.clowder.planning.model.VehicleSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The optimisation cross-checked against brute force. If the two ever disagree, the optimisation is
 * wrong, not the baseline.
 *
 * <p>Delaying a stop is simulated by moving its window open time forward, which forces the service
 * start and pushes the whole tail. The delayed stop's own window end is left alone, so a delay past
 * it fails on the stop itself — that is the {@code own} half of the recurrence, and it has to be
 * tested too. An earlier draft of this test widened the window end as well, which disabled that half
 * and made the "slack + 1 must break" assertion false wherever the stop's own window was the binding
 * term.
 */
class ForwardTimeSlackTest {

    /**
     * On the line every stop's own window binds: slack is 90, 110, 120 with 250 minutes to spare on
     * the return.
     */
    @Test
    void slackIsTheExactBoundaryWhenEachStopsOwnWindowBinds() {
        final var instance = TestInstances.line();
        final var route = new int[] {0, 1, 2};

        final var slack = ForwardTimeSlack.compute(route, route.length, instance.vehicles().get(0), instance);

        assertThat(slack).containsExactly(90, 110, 120);
        assertExactBoundaryAtEveryPosition(instance, route);
    }

    /**
     * The downstream half. Stop 30 opens at 60, so the vehicle arrives at 40 and waits 20. Stop 60
     * closes at 110; served at 100, its own slack is 10. The shift ends at 180 and the vehicle is back
     * at 170, so the return binds at 10 as well. Backwards: slack[2] = min(10, 10) = 10. Position 1
     * can absorb wait[2] + slack[2] = 0 + 10 against its own 90: 10. Position 0 can absorb
     * wait[1] + slack[1] = 20 + 10 = 30 against its own 90: 30 — the wait at 30 soaks up twenty
     * minutes of delay before anything downstream notices.
     */
    @Test
    void slackIsTheExactBoundaryWhenTheTailAndTheReturnBind() {
        final var instance = tightTail();
        final var route = new int[] {0, 1, 2};

        final var slack = ForwardTimeSlack.compute(route, route.length, instance.vehicles().get(0), instance);

        assertThat(slack).containsExactly(30, 10, 10);
        assertExactBoundaryAtEveryPosition(instance, route);
    }

    @Test
    void anEmptyRouteHasNoSlackEntries() {
        final var instance = TestInstances.line();

        assertThat(ForwardTimeSlack.compute(new int[0], 0, instance.vehicles().get(0), instance)).isEmpty();
    }

    /** Randomised: the exact-boundary property must hold on every feasible route, at every position. */
    @Test
    void slackIsTheExactBoundaryOnRandomFeasibleRoutes() {
        final var random = new Random(20260105L);
        var routesChecked = 0;

        for (var trial = 0; trial < 300; trial++) {
            final var instance = randomInstance(random);
            final var route = randomFeasibleRoute(random, instance);
            if (route.length == 0) {
                continue;
            }
            assertExactBoundaryAtEveryPosition(instance, route);
            routesChecked++;
        }

        assertThat(routesChecked).isGreaterThan(200);
    }

    /**
     * The safety property behind wiring slack into insertion: whenever the cheap test says a candidate
     * would push the next stop past its slack, the full scan must agree the insertion is infeasible.
     * The converse is not required — slack knows nothing about capacity or the inserted stop's own
     * window — which is why the full scan still runs on everything slack lets through.
     */
    @Test
    void pruningNeverRejectsAFeasibleInsertion() {
        final var random = new Random(20260105L);
        var pruned = 0;
        var considered = 0;

        for (var trial = 0; trial < 300; trial++) {
            final var instance = randomInstance(random);
            final var vehicle = instance.vehicles().get(0);
            final var route = randomFeasibleRoute(random, instance);
            if (route.length == 0) {
                continue;
            }

            final var serviceStarts = new int[route.length];
            RouteCalculator.serviceStarts(route, route.length, vehicle, instance, serviceStarts);
            final var slack = ForwardTimeSlack.compute(route, route.length, vehicle, instance);

            for (var candidate = 0; candidate < instance.stopCount(); candidate++) {
                if (contains(route, candidate)) {
                    continue;
                }
                for (var at = 0; at < route.length; at++) {
                    considered++;
                    final var pushed = ForwardTimeSlack.pushOnNext(
                            route, route.length, at, candidate, serviceStarts, vehicle, instance);
                    if (pushed <= slack[at]) {
                        continue;
                    }
                    pruned++;
                    final var trialRoute = RouteCalculator.withInsertion(route, route.length, at, candidate);
                    assertThat(RouteCalculator.isFeasible(trialRoute, trialRoute.length, vehicle, instance))
                            .as("trial %d: slack pruned a feasible insertion of %d at %d", trial, candidate, at)
                            .isFalse();
                }
            }
        }

        assertThat(pruned).as("the fixture must actually exercise pruning").isGreaterThan(100);
        assertThat(pruned).isLessThan(considered);
    }

    // ---- brute force ----

    private static void assertExactBoundaryAtEveryPosition(ProblemInstance instance, int[] route) {
        final var vehicle = instance.vehicles().get(0);
        final var slack = ForwardTimeSlack.compute(route, route.length, vehicle, instance);

        for (var position = 0; position < route.length; position++) {
            assertThat(slack[position]).as("slack[%d] on a feasible route", position).isGreaterThanOrEqualTo(0);

            assertThat(withDelay(instance, route, position, slack[position]))
                    .as("delay of exactly slack[%d]=%d must stay feasible", position, slack[position])
                    .isTrue();

            assertThat(withDelay(instance, route, position, slack[position] + 1))
                    .as("delay of slack[%d]+1 must break", position)
                    .isFalse();
        }
    }

    /** Forces service at {@code position} to start {@code delayMinutes} later than it does now. */
    private static boolean withDelay(ProblemInstance base, int[] route, int position, int delayMinutes) {
        final var vehicle = base.vehicles().get(0);
        final var stops = new ArrayList<>(base.stops());
        final var target = stops.get(route[position]);
        final var delayedStart = serviceStartOf(base, route, position) + delayMinutes;

        if (delayedStart > target.windowEndMinutes()) {
            return false; // the stop's own window, which StopSpec would refuse to express
        }

        stops.set(
                route[position],
                new StopSpec(
                        target.consignmentId(),
                        target.index(),
                        target.weightGrams(),
                        target.volumeMillilitres(),
                        target.serviceMinutes(),
                        delayedStart,
                        target.windowEndMinutes()));

        final var shifted = new ProblemInstance(base.vehicles(), stops, base.matrix(), base.depotIndex());
        return RouteCalculator.isFeasible(route, route.length, vehicle, shifted);
    }

    private static int serviceStartOf(ProblemInstance instance, int[] route, int position) {
        final var starts = new int[route.length];
        RouteCalculator.serviceStarts(route, route.length, instance.vehicles().get(0), instance, starts);
        return starts[position];
    }

    // ---- fixtures ----

    /** The M2 line with a wait in the middle, a tight window at the end, and a tight shift. */
    private static ProblemInstance tightTail() {
        var matrix = DistanceMatrix.from(
                List.of(LatLon.of(0d, 0d), LatLon.of(0d, 10d), LatLon.of(0d, 30d), LatLon.of(0d, 60d)),
                new EuclideanDistanceProvider());
        var stops = List.of(
                new StopSpec(100L, 1, 0L, 0L, 10, 0, 100),
                new StopSpec(200L, 2, 0L, 0L, 10, 60, 150),
                new StopSpec(300L, 3, 0L, 0L, 10, 0, 110));
        var vehicle = new VehicleSpec(1L, 100_000L, 100_000L, 0, 180, 0);
        return new ProblemInstance(List.of(vehicle), stops, matrix, 0);
    }

    private static ProblemInstance randomInstance(Random random) {
        final var stopCount = 2 + random.nextInt(7);

        final var points = new ArrayList<LatLon>();
        points.add(LatLon.of(0d, 0d));
        for (var i = 0; i < stopCount; i++) {
            points.add(LatLon.of(random.nextInt(60), random.nextInt(60)));
        }

        final var stops = new ArrayList<StopSpec>();
        for (var i = 0; i < stopCount; i++) {
            final var windowStart = random.nextInt(200);
            final var windowEnd = windowStart + 20 + random.nextInt(300);
            stops.add(new StopSpec(i, i + 1, 0L, 0L, random.nextInt(15), windowStart, windowEnd));
        }

        final var matrix = DistanceMatrix.from(points, new EuclideanDistanceProvider());
        final var vehicle = new VehicleSpec(1L, Long.MAX_VALUE, Long.MAX_VALUE, 0, 400 + random.nextInt(300), 0);
        return new ProblemInstance(List.of(vehicle), stops, matrix, 0);
    }

    /** Greedy: append stops in a random order, keeping each that stays feasible. May be empty. */
    private static int[] randomFeasibleRoute(Random random, ProblemInstance instance) {
        final var vehicle = instance.vehicles().get(0);
        final var order = new ArrayList<Integer>();
        for (var i = 0; i < instance.stopCount(); i++) {
            order.add(i);
        }
        java.util.Collections.shuffle(order, random);

        var route = new int[0];
        for (var position : order) {
            final var extended = RouteCalculator.withInsertion(route, route.length, route.length, position);
            if (RouteCalculator.isFeasible(extended, extended.length, vehicle, instance)) {
                route = extended;
            }
        }
        return route;
    }

    private static boolean contains(int[] route, int position) {
        for (var p : route) {
            if (p == position) {
                return true;
            }
        }
        return false;
    }
}
