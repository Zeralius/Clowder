package dev.zeralius.clowder.fleet.domain;

import dev.zeralius.clowder.common.error.ConflictException;
import dev.zeralius.clowder.common.error.NotFoundException;
import dev.zeralius.clowder.common.web.PageResponse;
import dev.zeralius.clowder.fleet.FleetFacade;
import dev.zeralius.clowder.fleet.api.CreateDepotRequest;
import dev.zeralius.clowder.fleet.api.DepotResponse;
import dev.zeralius.clowder.fleet.api.DriverResponse;
import dev.zeralius.clowder.fleet.api.UpdateDepotRequest;
import dev.zeralius.clowder.fleet.api.VehicleResponse;
import dev.zeralius.clowder.fleet.persistence.DepotRepository;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the cross-module facade and owns depot CRUD. Depot creation is deliberately not on the
 * facade: the facade is about what leaves the module, not a rule that everything must pass through
 * it, and no other module should be creating depots.
 */
@Service
@Transactional(readOnly = true)
public class FleetService implements FleetFacade {

    private final DepotRepository depots;
    private final VehicleService vehicles;
    private final DriverService drivers;

    public FleetService(DepotRepository depots, VehicleService vehicles, DriverService drivers) {
        this.depots = depots;
        this.vehicles = vehicles;
        this.drivers = drivers;
    }

    @Override
    public List<DepotResponse> findAllDepots() {
        return depots.findAll().stream().map(DepotMapper::toResponse).toList();
    }

    @Override
    public List<VehicleResponse> findActiveVehiclesAt(Long depotId) {
        return vehicles.findActiveVehiclesAt(depotId);
    }

    @Override
    public List<DriverResponse> findActiveDriversAt(Long depotId) {
        return drivers.findActiveDriversAt(depotId);
    }

    public PageResponse<DepotResponse> findAllDepots(Pageable pageable) {
        return PageResponse.from(depots.findAll(pageable), DepotMapper::toResponse);
    }

    @Override
    public DepotResponse findDepotById(Long id) {
        return depots.findById(id).map(DepotMapper::toResponse).orElseThrow(() -> new NotFoundException("Depot", id));
    }

    @Transactional
    public DepotResponse createDepot(CreateDepotRequest request) {
        if (depots.findByCode(request.code()).isPresent()) {
            throw new ConflictException("A depot with code '%s' already exists".formatted(request.code()));
        }
        var depot = new Depot(
                request.code(),
                request.name(),
                request.latitude(),
                request.longitude(),
                request.timezone(),
                request.distanceModelOrDefault());
        return DepotMapper.toResponse(depots.save(depot));
    }

    /**
     * No {@code save()}: the entity is managed, so dirty checking flushes at commit. Same shape as
     * {@code VehicleService.update}, and the {@code @Version} column added with the lifecycle is what
     * stops two dispatchers overwriting each other.
     */
    @Transactional
    public DepotResponse updateDepot(Long id, UpdateDepotRequest request) {
        var depot = depots.findById(id).orElseThrow(() -> new NotFoundException("Depot", id));
        depot.rename(request.name());
        depot.relocate(request.latitude(), request.longitude(), request.distanceModel());
        depot.moveToTimezone(request.timezone());
        return DepotMapper.toResponse(depot);
    }

    /**
     * Deactivating rather than deleting, the same verb as vehicles and drivers (decision D3). A depot
     * is referenced by plans and consignments that still have to be readable next year.
     */
    @Transactional
    public void deactivateDepot(Long id) {
        var depot = depots.findById(id).orElseThrow(() -> new NotFoundException("Depot", id));
        depot.deactivate();
    }

    @Transactional
    public DepotResponse reactivateDepot(Long id) {
        var depot = depots.findById(id).orElseThrow(() -> new NotFoundException("Depot", id));
        depot.reactivate();
        return DepotMapper.toResponse(depot);
    }

    /**
     * For callers that need the depot to still be in service, such as starting a plan run. A closed
     * depot can be read all day; it just cannot be given new work.
     */
    @Override
    public DepotResponse findActiveDepotById(Long id) {
        var depot = findDepotById(id);
        if (!depot.active()) {
            throw new ConflictException("Depot %d is not active".formatted(id));
        }
        return depot;
    }

    /** Used by the importer: idempotent, so re-importing an instance does not fail. */
    @Transactional
    public DepotResponse findOrCreateDepot(CreateDepotRequest request) {
        return depots.findByCode(request.code()).map(DepotMapper::toResponse).orElseGet(() -> createDepot(request));
    }
}
