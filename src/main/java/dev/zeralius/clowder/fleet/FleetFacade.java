package dev.zeralius.clowder.fleet;

import dev.zeralius.clowder.fleet.api.DepotResponse;
import dev.zeralius.clowder.fleet.api.VehicleResponse;
import java.util.List;

/**
 * What other modules may know about {@code fleet}. Deliberately narrow: everything here is something
 * planning or execution actually needs, not everything the module can do.
 */
public interface FleetFacade {

    List<DepotResponse> findAllDepots();

    List<VehicleResponse> findActiveVehiclesAt(Long depotId);
}