package dev.zeralius.clowder.execution.persistence;

import dev.zeralius.clowder.execution.domain.DriverEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverEventRepository extends JpaRepository<DriverEvent, Long> {

    Optional<DriverEvent> findByIdempotencyKey(String idempotencyKey);

    /**
     * Ordered by device time, then id. The id tie-break is what makes the projection deterministic
     * when two events share a timestamp, which happens constantly with a burst upload.
     */
    List<DriverEvent> findByPlanIdOrderByOccurredAtAscIdAsc(Long planId);

    long countByPlanId(Long planId);
}
