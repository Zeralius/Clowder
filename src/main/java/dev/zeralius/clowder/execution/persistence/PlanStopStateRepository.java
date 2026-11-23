package dev.zeralius.clowder.execution.persistence;

import dev.zeralius.clowder.execution.domain.PlanStopState;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PlanStopStateRepository extends JpaRepository<PlanStopState, Long> {

    List<PlanStopState> findByPlanIdOrderByPlanStopIdAsc(Long planId);

    /**
     * One statement, not a load-then-remove loop: a rebuild drops every row for the plan and there is
     * nothing to be gained from pulling them into the persistence context first. {@code @Transactional}
     * because a bulk delete needs one and this is called both inside a rebuild and on its own.
     */
    @Modifying
    @Transactional
    @Query("delete from PlanStopState s where s.planId = :planId")
    int deleteByPlanId(@Param("planId") Long planId);
}
