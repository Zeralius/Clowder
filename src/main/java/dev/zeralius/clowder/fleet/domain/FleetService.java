package dev.zeralius.clowder.fleet.domain;

import dev.zeralius.clowder.fleet.FleetFacade;
import dev.zeralius.clowder.fleet.api.DepotResponse;
import dev.zeralius.clowder.fleet.api.DriverResponse;
import dev.zeralius.clowder.fleet.api.VehicleResponse;
import dev.zeralius.clowder.fleet.persistence.DepotRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

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
}
