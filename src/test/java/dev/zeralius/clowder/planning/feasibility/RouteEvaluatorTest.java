package dev.zeralius.clowder.planning.feasibility;

import static dev.zeralius.clowder.planning.feasibility.TestGrid.instance;
import static dev.zeralius.clowder.planning.feasibility.TestGrid.route;
import static dev.zeralius.clowder.planning.feasibility.TestGrid.stop;
import static dev.zeralius.clowder.planning.feasibility.TestGrid.units;
import static dev.zeralius.clowder.planning.feasibility.TestGrid.vehicle;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Every row of the M2 test matrix, plus peak load and cumulative distance, which the matrix implies. */
class RouteEvaluatorTest {

    private final RouteEvaluator evaluator = new RouteEvaluator();

    @Nested
    class DegenerateRoutes {

        @Test
        void anEmptyRouteIsFeasibleAndCostsNothing() {
            var result = evaluator.evaluate(vehicle(), route(), instance());

            assertThat(result.feasible()).isTrue();
            assertThat(result.totalDistance()).isZero();
            assertThat(result.totalDurationMinutes()).isZero();
            assertThat(result.totalWaitMinutes()).isZero();
            assertThat(result.peakLoadGrams()).isZero();
            assertThat(result.schedule()).isEmpty();
            assertThat(result.violations()).isEmpty();
        }

        @Test
        void aNullRouteIsTreatedAsEmpty() {
            var result = evaluator.evaluate(vehicle(), null, instance());

            assertThat(result.feasible()).isTrue();
            assertThat(result.schedule()).isEmpty();
        }
    }

    @Nested
    class Scheduling {

        /** Depot to index 1 is 10 units, so 10 minutes; service 15; back is another 10. */
        @Test
        void computesTheScheduleForASingleStop() {
            var result = evaluator.evaluate(vehicle(), route(0), instance(stop(1, 0, 500, 15)));

            assertThat(result.feasible()).isTrue();
            assertThat(result.totalDistance()).isEqualTo(units(20));
            assertThat(result.totalDurationMinutes()).isEqualTo(35);

            var visit = result.schedule().get(0);
            assertThat(visit.stopIndex()).isZero();
            assertThat(visit.arrivalMinutes()).isEqualTo(10);
            assertThat(visit.serviceStartMinutes()).isEqualTo(10);
            assertThat(visit.waitMinutes()).isZero();
            assertThat(visit.departureMinutes()).isEqualTo(25);
            assertThat(visit.cumulativeDistance()).isEqualTo(units(10));
        }

        /** 0 to 1 is 10, 1 to 2 is 20, 2 to 3 is 30, 3 back to depot is 60. */
        @Test
        void accumulatesDistanceAlongTheRouteAndBack() {
            var result = evaluator.evaluate(
                    vehicle(),
                    route(0, 1, 2),
                    instance(stop(1, 0, 1000, 0), stop(2, 0, 1000, 0), stop(3, 0, 1000, 0)));

            assertThat(result.schedule())
                    .extracting(StopSchedule::cumulativeDistance)
                    .containsExactly(units(10), units(30), units(60));
            assertThat(result.totalDistance()).isEqualTo(units(120));
        }

        /** Visit order comes from the route array, not from the order stops were declared. */
        @Test
        void visitsStopsInRouteOrderNotDeclarationOrder() {
            var result = evaluator.evaluate(
                    vehicle(), route(1, 0), instance(stop(1, 0, 1000, 0), stop(3, 0, 1000, 0)));

            assertThat(result.schedule()).extracting(StopSchedule::stopIndex).containsExactly(1, 0);
            // depot to 3 is 60, 3 to 1 is 50, 1 back to depot is 10.
            assertThat(result.totalDistance()).isEqualTo(units(120));
        }
    }

    @Nested
    class Waiting {

        @Test
        void arrivingEarlyRecordsWaitAndIsNotAViolation() {
            var result = evaluator.evaluate(vehicle(), route(0), instance(stop(1, 100, 500, 15)));

            assertThat(result.feasible()).isTrue();
            assertThat(result.violations()).isEmpty();

            var visit = result.schedule().get(0);
            assertThat(visit.arrivalMinutes()).isEqualTo(10);
            assertThat(visit.waitMinutes()).isEqualTo(90);
            assertThat(visit.serviceStartMinutes()).isEqualTo(100);
            assertThat(visit.departureMinutes()).isEqualTo(115);
        }

        /**
         * Arrive 10, wait to 60. Leave 60, arrive 80, wait to 150. Leave 150, arrive 180, wait to 250.
         * Total 50 + 70 + 70.
         */
        @Test
        void sumsWaitAcrossEveryStop() {
            var result = evaluator.evaluate(
                    vehicle(),
                    route(0, 1, 2),
                    instance(stop(1, 60, 1000, 0), stop(2, 150, 1000, 0), stop(3, 250, 1000, 0)));

            assertThat(result.feasible()).isTrue();
            assertThat(result.schedule())
                    .extracting(StopSchedule::waitMinutes)
                    .containsExactly(50, 70, 70);
            assertThat(result.totalWaitMinutes()).isEqualTo(190);
            assertThat(result.totalDurationMinutes()).isEqualTo(310);
        }
    }

    @Nested
    class TimeWindows {

        @Test
        void reportsAStopReachedAfterItsWindowClosed() {
            var result = evaluator.evaluate(vehicle(), route(0), instance(stop(1, 0, 5, 15)));

            assertThat(result.feasible()).isFalse();
            assertThat(result.violationsOfType(Violation.TimeWindowMissed.class))
                    .containsExactly(new Violation.TimeWindowMissed(100L, 10, 5));
        }

        /** Decision D2: the window is closed, so starting exactly at the end is legal. */
        @Test
        void arrivingExactlyAtTheWindowEndIsFeasible() {
            var result = evaluator.evaluate(vehicle(), route(0), instance(stop(1, 0, 10, 15)));

            assertThat(result.feasible()).isTrue();
            assertThat(result.violations()).isEmpty();
        }

        /** Standard VRPTW constrains the start of service, never its finish. */
        @Test
        void serviceRunningPastTheWindowEndIsFeasible() {
            var result = evaluator.evaluate(vehicle(), route(0), instance(stop(1, 0, 10, 15)));

            assertThat(result.feasible()).isTrue();
            assertThat(result.schedule().get(0).departureMinutes()).isEqualTo(25);
        }

        /**
         * A 200-minute service at the first stop pushes both later stops past their windows. Both must
         * be reported: a solver needs to know how bad a move is, not merely that it was bad.
         */
        @Test
        void reportsEveryDownstreamStopNotJustTheFirst() {
            var result = evaluator.evaluate(
                    vehicle(),
                    route(0, 1, 2),
                    instance(stop(1, 0, 1000, 200), stop(2, 0, 100, 0), stop(3, 0, 100, 0)));

            assertThat(result.violationsOfType(Violation.TimeWindowMissed.class))
                    .containsExactly(
                            new Violation.TimeWindowMissed(200L, 230, 100),
                            new Violation.TimeWindowMissed(300L, 260, 100));
        }
    }

    @Nested
    class Capacity {

        @Test
        void reportsWeightOverloadOnly() {
            var result = evaluator.evaluate(
                    vehicle(TestGrid.SHIFT_MINUTES, 1_000L, 100_000L),
                    route(0, 1),
                    instance(stop(1, 0, 1000, 0, 600L, 0L), stop(2, 0, 1000, 0, 600L, 0L)));

            assertThat(result.violations())
                    .containsExactly(new Violation.CapacityExceeded(
                            CapacityKind.WEIGHT, 1_000L, 1_200L, RouteEvaluator.DEPOT_DEPARTURE));
        }

        @Test
        void reportsBothDimensionsWhenBothAreExceeded() {
            var result = evaluator.evaluate(
                    vehicle(TestGrid.SHIFT_MINUTES, 1_000L, 500L),
                    route(0, 1),
                    instance(stop(1, 0, 1000, 0, 600L, 300L), stop(2, 0, 1000, 0, 600L, 300L)));

            assertThat(result.violationsOfType(Violation.CapacityExceeded.class))
                    .extracting(Violation.CapacityExceeded::kind)
                    .containsExactly(CapacityKind.WEIGHT, CapacityKind.VOLUME);
        }

        /** Deliveries only, so the peak is what left the depot and the load falls from there. */
        @Test
        void tracksPeakLoadAndTheRemainderAfterEachStop() {
            var result = evaluator.evaluate(
                    vehicle(),
                    route(0, 1, 2),
                    instance(
                            stop(1, 0, 1000, 0, 100L, 0L),
                            stop(2, 0, 1000, 0, 200L, 0L),
                            stop(3, 0, 1000, 0, 300L, 0L)));

            assertThat(result.peakLoadGrams()).isEqualTo(600L);
            assertThat(result.schedule())
                    .extracting(StopSchedule::loadAfter)
                    .containsExactly(500L, 300L, 0L);
        }

        @Test
        void loadExactlyAtCapacityIsFeasible() {
            var result = evaluator.evaluate(
                    vehicle(TestGrid.SHIFT_MINUTES, 1_000L, 1_000L),
                    route(0),
                    instance(stop(1, 0, 1000, 0, 1_000L, 1_000L)));

            assertThat(result.feasible()).isTrue();
        }
    }

    @Nested
    class Shift {

        /**
         * Depot to index 3 is 60 each way. Service finishes at 75, inside the 100-minute shift, but the
         * vehicle does not get back until 135.
         */
        @Test
        void reportsALateDepotReturnWithoutAShiftOverrun() {
            var result = evaluator.evaluate(vehicle(100), route(0), instance(stop(3, 0, 1000, 15)));

            assertThat(result.violations())
                    .containsExactly(new Violation.DepotReturnMissed(135, 100));
        }

        /** A longer service overruns the shift as well as the return. */
        @Test
        void reportsBothWhenWorkItselfOverrunsTheShift() {
            var result = evaluator.evaluate(vehicle(100), route(0), instance(stop(3, 0, 1000, 60)));

            assertThat(result.violations())
                    .containsExactly(
                            new Violation.ShiftOverrun(120, 100), new Violation.DepotReturnMissed(180, 100));
        }

        @Test
        void returningExactlyAtTheShiftEndIsFeasible() {
            // 60 out, service 15, 60 back = 135.
            var result = evaluator.evaluate(vehicle(135), route(0), instance(stop(3, 0, 1000, 15)));

            assertThat(result.feasible()).isTrue();
        }
    }

    @Nested
    class ViolationOrdering {

        /**
         * Capacity first, then window misses in visit order, then shift overrun, then depot return.
         *
         * <p>On paper: 1200 g leaves the depot against a 1000 g limit. Arrive at index 1 at minute 10,
         * window closed at 5. Leave at 10, arrive at index 3 at 60, window closed at 5. Service is
         * instant, so work ends at 60 — already past the 50-minute shift. Return is another 60, so the
         * vehicle is back at 120. All four kinds fire, in this order.
         */
        @Test
        void ordersViolationsDeterministically() {
            var result = evaluator.evaluate(
                    vehicle(50, 1_000L, 100_000L),
                    route(0, 1),
                    instance(stop(1, 0, 5, 0, 600L, 0L), stop(3, 0, 5, 0, 600L, 0L)));

            assertThat(result.violations())
                    .extracting(v -> v.getClass().getSimpleName())
                    .containsExactly(
                            "CapacityExceeded",
                            "TimeWindowMissed",
                            "TimeWindowMissed",
                            "ShiftOverrun",
                            "DepotReturnMissed");
        }
    }
}
