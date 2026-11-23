package dev.zeralius.clowder.fleet.persistence;

import dev.zeralius.clowder.fleet.domain.Vehicle;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    /**
     * Racy on purpose: this exists for the friendly 409 message. The unique constraint on
     * {@code vehicle.registration} is the actual guarantee, and
     * {@code DataIntegrityViolationException} is what catches the lost race.
     */
    boolean existsByRegistration(String registration);

    @Query("select v from Vehicle v join fetch v.depot where v.id = :id")
    Optional<Vehicle> findByIdWithDepot(@Param("id") Long id);

    /** Overridden purely to attach the entity graph, so listing does not N+1 on depot. */
    @Override
    @EntityGraph(attributePaths = "depot")
    Page<Vehicle> findAll(Pageable pageable);

    /** Backs {@code FleetFacade.findActiveVehiclesAt}; M3 is the eventual caller. */
    @EntityGraph(attributePaths = "depot")
    List<Vehicle> findByDepotIdAndActiveTrue(Long depotId);
}
