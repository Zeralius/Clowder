package dev.zeralius.clowder.fleet;

import dev.zeralius.clowder.fleet.api.DepotResponse;
import dev.zeralius.clowder.fleet.api.DriverResponse;
import dev.zeralius.clowder.fleet.api.VehicleResponse;
import java.util.List;

/**
 * What other modules may know about {@code fleet}. Deliberately narrow: everything here is something
 * planning or execution actually needs, not everything the module can do.
 */
public interface FleetFacade {

    List<DepotResponse> findAllDepots();

    /** One depot by id, with its coordinates. Planning and execution both need the depot location. */
    DepotResponse findDepotById(Long id);

    /**
     * The same, but refusing a depot that has been closed. Reading a closed depot is fine; giving it
     * new work is not, so a plan run goes through this one.
     */
    DepotResponse findActiveDepotById(Long id);

    List<VehicleResponse> findActiveVehiclesAt(Long depotId);

    List<DriverResponse> findActiveDriversAt(Long depotId);
}
