package dev.zeralius.clowder.execution.persistence;

import dev.zeralius.clowder.execution.domain.PlanExecutionState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanExecutionStateRepository extends JpaRepository<PlanExecutionState, Long> {}
