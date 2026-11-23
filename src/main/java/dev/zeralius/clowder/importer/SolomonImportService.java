package dev.zeralius.clowder.importer;

import dev.zeralius.clowder.common.geo.DistanceModel;
import dev.zeralius.clowder.common.time.TimeWindow;
import dev.zeralius.clowder.consignment.api.CreateConsignmentRequest;
import dev.zeralius.clowder.consignment.domain.ConsignmentService;
import dev.zeralius.clowder.fleet.api.CreateDepotRequest;
import dev.zeralius.clowder.fleet.api.CreateVehicleRequest;
import dev.zeralius.clowder.fleet.domain.FleetService;
import dev.zeralius.clowder.fleet.domain.VehicleService;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a Solomon VRPTW instance into depot, vehicles and consignments — through the ordinary service
 * layer, so the imported data obeys exactly the same invariants as anything created by hand. Every
 * consignment goes through {@code ConsignmentService.create}: reference uniqueness, the depot check,
 * value-object validation and the {@code PENDING} starting state, the same path a manual POST takes.
 *
 * <p>Lives outside both modules on purpose. It orchestrates {@code fleet} and {@code consignment}
 * through their services, and the ArchUnit rule that keeps {@code consignment} out of
 * {@code fleet.domain} would (correctly) reject it there.
 *
 * <p>One transaction for the whole import: the inner service methods join it, so 125 writes commit
 * or roll back together. A half-imported R101 is worse than none.
 */
@Service
public class SolomonImportService {

    /** The format carries no date, so pick one and be consistent. Depot timezone is UTC to match. */
    public static final LocalDate DEFAULT_SERVICE_DATE = LocalDate.of(2026, 1, 5);

    /** Decision D4: the volume dimension stays in the schema but is inactive for benchmarks. */
    private static final long INACTIVE_VOLUME_CAPACITY = 1_000_000_000L;

    /** Unused for Solomon: travel time equals distance, so M2 asks the DistanceProvider instead. */
    private static final BigDecimal NOMINAL_SPEED_KPH = new BigDecimal("60.00");

    private static final double MAX_LATITUDE = 90d;
    private static final double MAX_LONGITUDE = 180d;

    private final SolomonInstanceParser parser;
    private final FleetService fleet;
    private final VehicleService vehicles;
    private final ConsignmentService consignments;

    public SolomonImportService(
            SolomonInstanceParser parser,
            FleetService fleet,
            VehicleService vehicles,
            ConsignmentService consignments) {
        this.parser = parser;
        this.fleet = fleet;
        this.vehicles = vehicles;
        this.consignments = consignments;
    }

    @Transactional
    public SolomonImportResult importFrom(Reader reader, LocalDate serviceDate) {
        var instance = parser.parse(reader);
        return importInstance(instance, serviceDate == null ? DEFAULT_SERVICE_DATE : serviceDate);
    }

    @Transactional
    public SolomonImportResult importInstance(SolomonInstance instance, LocalDate serviceDate) {
        assertCoordinatesFitTheGeographicModel(instance);

        var midnight = serviceDate.atStartOfDay(ZoneOffset.UTC).toInstant();

        var depot = fleet.findOrCreateDepot(new CreateDepotRequest(
                instance.name(),
                instance.name() + " (Solomon instance)",
                latitudeOf(instance.depot()),
                longitudeOf(instance.depot()),
                "UTC",
                // Explicit, and the whole reason the column exists: these coordinates are abstract
                // plane units, and projecting them onto a sphere would destroy the comparison with
                // the published best-known results.
                DistanceModel.EUCLIDEAN));

        for (var i = 1; i <= instance.vehicleCount(); i++) {
            vehicles.create(new CreateVehicleRequest(
                    depot.id(),
                    "%s-V%02d".formatted(instance.name(), i),
                    instance.capacity(),
                    INACTIVE_VOLUME_CAPACITY,
                    LocalTime.MIDNIGHT,
                    instance.horizonMinutes(),
                    NOMINAL_SPEED_KPH));
        }

        for (var customer : instance.customers()) {
            consignments.create(new CreateConsignmentRequest(
                    "%s-%03d".formatted(instance.name(), customer.id()),
                    depot.id(),
                    serviceDate,
                    latitudeOf(customer),
                    longitudeOf(customer),
                    "Solomon customer %d".formatted(customer.id()),
                    customer.demand(),
                    0L,
                    customer.serviceTime(),
                    midnight.plusSeconds(customer.readyTime() * 60L),
                    windowEnd(midnight, customer)));
        }

        return new SolomonImportResult(
                instance.name(),
                depot.id(),
                depot.code(),
                instance.vehicleCount(),
                instance.customers().size(),
                instance.horizonMinutes(),
                serviceDate);
    }

    /**
     * A Solomon due date can equal its ready time in a degenerate row, but {@link TimeWindow} requires
     * a non-empty interval. Fail loudly rather than silently widening someone's benchmark.
     */
    private static Instant windowEnd(Instant midnight, SolomonCustomer customer) {
        if (customer.dueTime() <= customer.readyTime()) {
            throw new IllegalArgumentException("Customer %d has due time %d not after ready time %d"
                    .formatted(customer.id(), customer.dueTime(), customer.readyTime()));
        }
        return midnight.plusSeconds(customer.dueTime() * 60L);
    }

    /** x maps to longitude (the wider bound); y maps to latitude. */
    private static BigDecimal latitudeOf(SolomonCustomer customer) {
        return BigDecimal.valueOf(customer.y());
    }

    private static BigDecimal longitudeOf(SolomonCustomer customer) {
        return BigDecimal.valueOf(customer.x());
    }

    private static void assertCoordinatesFitTheGeographicModel(SolomonInstance instance) {
        var all = new ArrayList<SolomonCustomer>(instance.customers());
        all.add(instance.depot());

        for (var c : all) {
            if (Math.abs(c.y()) > MAX_LATITUDE || Math.abs(c.x()) > MAX_LONGITUDE) {
                throw new IllegalArgumentException(
                        ("Instance %s customer %d at (x=%s, y=%s) falls outside the geographic bounds "
                                        + "LatLon enforces. Solomon coordinates are plane units, not degrees. "
                                        + "Either translate every coordinate by a constant (Euclidean distance is "
                                        + "translation-invariant, so published results still compare exactly) or "
                                        + "introduce a PlanarPoint type alongside LatLon. Do not widen LatLon.")
                                .formatted(instance.name(), c.id(), c.x(), c.y()));
            }
        }
    }
}
