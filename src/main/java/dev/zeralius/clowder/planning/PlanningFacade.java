package dev.zeralius.clowder.planning;

import dev.zeralius.clowder.planning.plan.api.PlanResponse;
import java.time.LocalDate;
import java.util.Optional;

/**
 * What execution may know about planning. Deliberately narrow: reading a plan is all execution needs,
 * and it gets DTOs rather than entities so the module boundary holds by construction.
 */
public interface PlanningFacade {

    Optional<PlanResponse> findPublishedPlan(Long vehicleId, LocalDate serviceDate);

    /**
     * One plan by id. Returns the DTO, never the entity, so execution can fold an event log over a
     * plan without ever holding a planning entity or reaching into its repositories.
     *
     * @throws dev.zeralius.clowder.common.error.NotFoundException when there is no such plan
     */
    PlanResponse findPlan(Long id);
}
