package dev.zeralius.clowder.planning.plan.domain;

import dev.zeralius.clowder.common.error.NotFoundException;
import dev.zeralius.clowder.common.geo.LatLon;
import dev.zeralius.clowder.consignment.ConsignmentFacade;
import dev.zeralius.clowder.consignment.api.ConsignmentResponse;
import dev.zeralius.clowder.fleet.FleetFacade;
import dev.zeralius.clowder.fleet.api.VehicleResponse;
import dev.zeralius.clowder.planning.model.DistanceMatrix;
import dev.zeralius.clowder.planning.model.ProblemInstance;
import dev.zeralius.clowder.planning.model.StopSpec;
import dev.zeralius.clowder.planning.model.VehicleSpec;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns database rows into the flat problem the solver takes. This is the only place the two worlds
 * meet — everything downstream is plain Java, everything upstream is JPA.
 *
 * <p>Lives in {@code planning.plan}, which is deliberately outside {@code planningCoreIsFrameworkFree}:
 * translating at the edge is exactly this package's job.
 */
@Service
public class PlanningProblemLoader {

    private final FleetFacade fleet;
    private final ConsignmentFacade consignments;

    public PlanningProblemLoader(FleetFacade fleet, ConsignmentFacade consignments) {
        this.fleet = fleet;
        this.consignments = consignments;
    }

    /** Short and read-only. Everything it returns is detached, immutable data. */
    @Transactional(readOnly = true)
    public LoadedProblem load(Long depotId, LocalDate serviceDate) {
        final var depot = fleet.findActiveDepotById(depotId);

        final var vehicles = fleet.findActiveVehiclesAt(depotId);
        if (vehicles.isEmpty()) {
            throw new NotFoundException("Active vehicle at depot", depotId);
        }

        final var work = consignments.findPlannableFor(depotId, serviceDate);

        final var zone = ZoneId.of(depot.timezone());
        final var epoch = serviceDate.atStartOfDay(zone).toInstant();

        // Matrix index 0 is the depot; stops follow in list order.
        final var points = new ArrayList<LatLon>(work.size() + 1);
        points.add(new LatLon(depot.latitude(), depot.longitude()));

        final var stops = new ArrayList<StopSpec>(work.size());
        for (var i = 0; i < work.size(); i++) {
            final var c = work.get(i);
            points.add(c.location());
            stops.add(new StopSpec(
                    c.id(),
                    i + 1,
                    c.weightGrams(),
                    c.volumeMillilitres(),
                    c.serviceMinutes(),
                    minutesFrom(epoch, c.window().start()),
                    minutesFrom(epoch, c.window().end())));
        }

        final var specs = new ArrayList<VehicleSpec>(vehicles.size());
        for (var v : vehicles) {
            specs.add(new VehicleSpec(
                    v.id(),
                    v.capacityGrams(),
                    v.capacityMillilitres(),
                    minutesFrom(
                            epoch,
                            v.shiftStart().atDate(serviceDate).atZone(zone).toInstant()),
                    v.shiftMinutes(),
                    0));
        }

        // The depot says which geometry its coordinates are in, so a benchmark import is measured
        // with Euclidean distance and a real depot with haversine, without anything here deciding.
        // One matrix serves every vehicle in the run, so travel time uses the slowest of them: a
        // matrix that is optimistic about travel time produces plans nobody can drive.
        final var matrix = DistanceMatrix.from(points, depot.distanceModel().provider(slowestSpeedKph(vehicles)));

        return new LoadedProblem(new ProblemInstance(specs, stops, matrix, 0), points, work, epoch, zone);
    }

    private static double slowestSpeedKph(List<VehicleResponse> vehicles) {
        return vehicles.stream()
                .map(VehicleResponse::avgSpeedKph)
                .min(java.util.Comparator.naturalOrder())
                .orElseThrow()
                .doubleValue();
    }

    private static int minutesFrom(Instant epoch, Instant when) {
        return Math.toIntExact(Duration.between(epoch, when).toMinutes());
    }

    /**
     * Everything the persist phase needs to turn a solution back into rows.
     *
     * @param consignments in stop order, so {@code consignments.get(position)} is the stop at that
     *     position — M4's plan_stop snapshot needs the reference and address, not just the id
     */
    public record LoadedProblem(
            ProblemInstance instance,
            List<LatLon> points,
            List<ConsignmentResponse> consignments,
            Instant epoch,
            ZoneId zone) {}
}
