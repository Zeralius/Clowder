package dev.zeralius.clowder.planning.plan.persistence;

import dev.zeralius.clowder.planning.plan.domain.Plan;
import dev.zeralius.clowder.planning.plan.domain.PlanStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    Optional<Plan> findByVehicleIdAndServiceDateAndStatus(Long vehicleId, LocalDate serviceDate, PlanStatus status);

    @EntityGraph(attributePaths = "stops")
    Optional<Plan> findWithStopsByVehicleIdAndServiceDateAndStatus(
            Long vehicleId, LocalDate serviceDate, PlanStatus status);

    @EntityGraph(attributePaths = "stops")
    List<Plan> findWithStopsByVehicleIdAndServiceDateOrderByVersionNoDesc(Long vehicleId, LocalDate serviceDate);

    @EntityGraph(attributePaths = "stops")
    Optional<Plan> findWithStopsById(Long id);

    List<Plan> findByPlanRunIdOrderByVehicleIdAsc(Long planRunId);

    /**
     * Next version number for a vehicle-day. Advisory only: the unique constraint on {@code
     * (vehicle_id, service_date, version_no)} is what actually stops a collision.
     */
    @Query("select coalesce(max(p.versionNo), 0) from Plan p "
            + "where p.vehicleId = :vehicleId and p.serviceDate = :serviceDate")
    int findMaxVersionNo(@Param("vehicleId") Long vehicleId, @Param("serviceDate") LocalDate serviceDate);
}
