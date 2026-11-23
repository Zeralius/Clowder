package dev.zeralius.clowder.planning.plan.persistence;

import dev.zeralius.clowder.planning.plan.domain.PlanRun;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRunRepository extends JpaRepository<PlanRun, Long> {

    @EntityGraph(attributePaths = "unassigned")
    Optional<PlanRun> findWithUnassignedById(Long id);

    List<PlanRun> findByDepotIdAndServiceDateOrderByStartedAtDesc(Long depotId, LocalDate serviceDate);
}
