package dev.zeralius.clowder.planning.plan.api;

import dev.zeralius.clowder.planning.plan.domain.PlanStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One vehicle, one day, one version.
 *
 * @param versionNo the version a dispatcher reads; v2 supersedes v1 rather than replacing it
 * @param totalDistance in the distance matrix minor units, not metres (decision D7)
 */
public record PlanResponse(
        Long id,
        Long planRunId,
        Long depotId,
        Long vehicleId,
        LocalDate serviceDate,
        int versionNo,
        PlanStatus status,
        long totalDistance,
        int totalDurationMinutes,
        Instant publishedAt,
        Instant createdAt,
        List<PlanStopResponse> stops) {}
