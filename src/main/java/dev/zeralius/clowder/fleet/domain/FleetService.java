package dev.zeralius.clowder.fleet.domain;

import dev.zeralius.clowder.fleet.FleetFacade;
import dev.zeralius.clowder.fleet.api.DepotResponse;
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

    public FleetService(DepotRepository depots, VehicleService vehicles) {
        this.depots = depots;
        this.vehicles = vehicles;
    }

    @Override
    public List<DepotResponse> findAllDepots() {
        return depots.findAll().stream().map(DepotMapper::toResponse).toList();
    }

    @Override
    public List<VehicleResponse> findActiveVehiclesAt(Long depotId) {
        return vehicles.findActiveVehicles(depotId);
    }
}
