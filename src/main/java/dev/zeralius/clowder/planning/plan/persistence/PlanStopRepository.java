package dev.zeralius.clowder.planning.plan.persistence;

import dev.zeralius.clowder.planning.plan.domain.PlanStop;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanStopRepository extends JpaRepository<PlanStop, Long> {

    List<PlanStop> findByConsignmentIdOrderBySequenceNoAsc(Long consignmentId);
}
