package dev.zeralius.clowder.fleet.domain;

import dev.zeralius.clowder.common.error.ConflictException;
import dev.zeralius.clowder.common.error.NotFoundException;
import dev.zeralius.clowder.common.web.PageResponse;
import dev.zeralius.clowder.fleet.api.CreateVehicleRequest;
import dev.zeralius.clowder.fleet.api.UpdateVehicleRequest;
import dev.zeralius.clowder.fleet.api.VehicleResponse;
import dev.zeralius.clowder.fleet.persistence.DepotRepository;
import dev.zeralius.clowder.fleet.persistence.VehicleRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class VehicleService {

    private final VehicleRepository vehicles;
    private final DepotRepository depots;

    public VehicleService(VehicleRepository vehicles, DepotRepository depots) {
        this.vehicles = vehicles;
        this.depots = depots;
    }

    public VehicleResponse findById(Long id) {
        return vehicles.findByIdWithDepot(id)
                .map(VehicleMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("vehicle", id));
    }

    public PageResponse<VehicleResponse> findAll(Pageable pageable) {
        return PageResponse.from(vehicles.findAll(pageable), VehicleMapper::toResponse);
    }


    /**
     * Backs the cross-module facade. A missing depot is a 404 rather than an empty list: asking for
     * the vehicles at depot 9999 is a different mistake from a depot that happens to have none.
     */
    public List<VehicleResponse> findActiveVehicles(Long depotId) {
        if(!depots.existsById(depotId)) {
            throw new NotFoundException("Depot", depotId);
        }
        return vehicles.findByDepotIdAndActiveTrue(depotId).stream()
                .map(VehicleMapper::toResponse)
                .toList();
    }

    @Transactional
    public VehicleResponse create(CreateVehicleRequest request) {
        if(vehicles.existsByRegistration(request.registration())) {
            throw new ConflictException("A vehicle with registration '%s' already exists".formatted(request.registration()));
        }
        var depot = depots.findById(request.depotId()).orElseThrow(() -> new NotFoundException("Depot", request.depotId()));

        var vehicle = new Vehicle(
                depot,
                request.registration(),
                request.capacityGrams(),
                request.capacityMillilitres(),
                request.shiftStart(),
                request.shiftMinutes(),
                request.avgSpeedKph());

        return VehicleMapper.toResponse(vehicles.save(vehicle));
    }

    @Transactional
    public VehicleResponse update(Long id, UpdateVehicleRequest request) {
        var vehicle = vehicles.findByIdWithDepot(id)
                .orElseThrow(() -> new NotFoundException("Vehicle", id));

        vehicle.updateCapacity(request.capacityGrams(), request.capacityMillilitres());
        vehicle.updateShift(request.shiftStart(), request.shiftMinutes());
        vehicle.updateAvgSpeed(request.avgSpeedKph());

        return VehicleMapper.toResponse(vehicle);
    }

    @Transactional
    public void deactivate(Long id) {
        vehicles.findById(id)
                .orElseThrow(() -> new NotFoundException("Vehicle", id))
                .deactivate();
    }


}
