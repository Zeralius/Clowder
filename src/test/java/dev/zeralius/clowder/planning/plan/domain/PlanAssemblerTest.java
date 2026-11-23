package dev.zeralius.clowder.planning.plan.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.common.geo.EuclideanDistanceProvider;
import dev.zeralius.clowder.common.geo.LatLon;
import dev.zeralius.clowder.common.time.TimeWindow;
import dev.zeralius.clowder.consignment.api.ConsignmentResponse;
import dev.zeralius.clowder.consignment.domain.ConsignmentStatus;
import dev.zeralius.clowder.planning.model.DistanceMatrix;
import dev.zeralius.clowder.planning.model.ProblemInstance;
import dev.zeralius.clowder.planning.model.StopSpec;
import dev.zeralius.clowder.planning.model.VehicleSpec;
import dev.zeralius.clowder.planning.solver.Route;
import dev.zeralius.clowder.planning.solver.Solution;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * One stop, ten plane units east of the depot, fifteen minutes of service. Everything here is
 * hand-computable, which is the point: the assembler is where solver minutes become instants a
 * driver will be held to.
 */
class PlanAssemblerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 1, 5);

    /** The service date at midnight UTC, as the loader builds it for a UTC depot. */
    private static final Instant EPOCH = Instant.parse("2026-01-05T00:00:00Z");

    private final PlanAssembler assembler = new PlanAssembler();

    private static ProblemInstance instance(int windowStart, int windowEnd, int shiftMinutes) {
        final var points = List.of(LatLon.of(0d, 0d), LatLon.of(0d, 10d));
        final var matrix = DistanceMatrix.from(points, new EuclideanDistanceProvider());
        final var stops = List.of(new StopSpec(42L, 1, 0L, 0L, 15, windowStart, windowEnd));
        final var vehicle = new VehicleSpec(7L, 1_000L, 1_000L, 0, shiftMinutes, 0);
        return new ProblemInstance(List.of(vehicle), stops, matrix, 0);
    }

    /** Position 0 in the instance is this consignment; the assembler snapshots its reference. */
    private static List<ConsignmentResponse> consignments() {
        return List.of(new ConsignmentResponse(
                42L,
                "MINI-001",
                1L,
                DAY,
                LatLon.of(0d, 10d),
                "Ten Units East",
                0L,
                0L,
                15,
                new TimeWindow(EPOCH, EPOCH.plusSeconds(3600)),
                ConsignmentStatus.PENDING));
    }

    private static Solution solution() {
        return new Solution(List.of(new Route(7L, new int[] {0}, 20_000_000L, 35)), new int[0], 20_000_000L);
    }

    private List<Plan> assemble(ProblemInstance instance, int versionNo) {
        return assembler.assemble(
                1L, solution(), instance, EPOCH, DAY, consignments(), vehicleId -> versionNo);
    }

    @Test
    void convertsSolverMinutesBackIntoInstants() {
        final var plans = assemble(instance(0, 500, 1000), 1);

        assertThat(plans).hasSize(1);
        final var stop = plans.getFirst().getStops().getFirst();

        // Ten units at speed one is ten minutes; service is fifteen; so departure is at minute 25.
        assertThat(stop.getPlannedArrival()).isEqualTo(Instant.parse("2026-01-05T00:10:00Z"));
        assertThat(stop.getPlannedServiceStart()).isEqualTo(Instant.parse("2026-01-05T00:10:00Z"));
        assertThat(stop.getPlannedDeparture()).isEqualTo(Instant.parse("2026-01-05T00:25:00Z"));
        assertThat(stop.getWaitMinutes()).isZero();
    }

    /**
     * Minute 1440 is midnight the next day. Nothing special should happen: {@code Instant} arithmetic
     * does not care about calendar days, and the instinct to special-case this is the bug.
     */
    @Test
    void crossesMidnightWithoutSpecialHandling() {
        final var plans = assemble(instance(1440, 2000, 2000), 1);
        final var stop = plans.getFirst().getStops().getFirst();

        assertThat(stop.getPlannedArrival()).isEqualTo(Instant.parse("2026-01-05T00:10:00Z"));
        assertThat(stop.getPlannedServiceStart()).isEqualTo(Instant.parse("2026-01-06T00:00:00Z"));
        assertThat(stop.getPlannedDeparture()).isEqualTo(Instant.parse("2026-01-06T00:15:00Z"));

        // Waiting from minute 10 until the window opens at 1440. Normal, and never a violation.
        assertThat(stop.getWaitMinutes()).isEqualTo(1430);
    }

    @Test
    void theServiceDateIsPassedInRatherThanDerivedFromTheEpoch() {
        final var plans = assemble(instance(0, 500, 1000), 1);

        assertThat(plans.getFirst().getServiceDate()).isEqualTo(DAY);
    }

    @Test
    void snapshotsTheReferenceAndAddressAlongsideTheId() {
        final var plans = assemble(instance(0, 500, 1000), 1);
        final var stop = plans.getFirst().getStops().getFirst();

        assertThat(stop.getSequenceNo()).isZero();
        assertThat(stop.getConsignmentId()).isEqualTo(42L);
        assertThat(stop.getReference()).isEqualTo("MINI-001");
        assertThat(stop.getAddressLine()).isEqualTo("Ten Units East");
    }

    @Test
    void takesItsVersionNumberFromTheSupplier() {
        final var plans = assemble(instance(0, 500, 1000), 3);

        assertThat(plans.getFirst().getVersionNo()).isEqualTo(3);
        assertThat(plans.getFirst().getVehicleId()).isEqualTo(7L);
        assertThat(plans.getFirst().getPlanRunId()).isEqualTo(1L);
    }

    /** Totals come from the evaluator, not from the route the solver handed over. */
    @Test
    void totalsComeFromTheEvaluator() {
        final var plans = assemble(instance(0, 500, 1000), 1);

        assertThat(plans.getFirst().getTotalDistance()).isEqualTo(20_000_000L);
        assertThat(plans.getFirst().getTotalDurationMinutes()).isEqualTo(35);
    }

    /** A vehicle that never left the depot was not used, so it gets no plan. */
    @Test
    void emptyRoutesProduceNoPlan() {
        final var empty = new Solution(List.of(new Route(7L, new int[0], 0L, 0)), new int[0], 0L);

        final var plans = assembler.assemble(
                1L, empty, instance(0, 500, 1000), EPOCH, DAY, consignments(), vehicleId -> 1);

        assertThat(plans).isEmpty();
    }
}
