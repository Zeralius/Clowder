package dev.zeralius.clowder.planning;

import dev.zeralius.clowder.planning.plan.api.PlanResponse;
import java.time.LocalDate;
import java.util.Optional;

/**
 * What execution may know about planning. Deliberately one method: M5 compares reality against the
 * published plan for a vehicle-day, and nothing else needs to reach in here.
 */
public interface PlanningFacade {

    Optional<PlanResponse> findPublishedPlan(Long vehicleId, LocalDate serviceDate);
}
