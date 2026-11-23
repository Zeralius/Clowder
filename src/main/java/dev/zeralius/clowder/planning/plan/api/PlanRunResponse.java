package dev.zeralius.clowder.planning.plan.api;

import dev.zeralius.clowder.planning.plan.domain.PlanRunStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One solver invocation, as stored. The routes themselves are not inlined here: each vehicle got its
 * own {@link PlanResponse}, readable at {@code GET /api/plans/{id}}.
 *
 * @param seed with {@code solverVersion}, enough to reproduce this run months later
 * @param unassignedReferences consignments no vehicle could take — empty in a complete plan
 * @param planIds the draft plans this run produced, one per vehicle used, lowest vehicle id first
 */
public record PlanRunResponse(
        Long id,
        Long depotId,
        LocalDate serviceDate,
        PlanRunStatus status,
        String solverVersion,
        long seed,
        int budgetMs,
        Long elapsedMs,
        Integer iterations,
        String failureReason,
        Instant startedAt,
        Instant finishedAt,
        List<String> unassignedReferences,
        List<Long> planIds) {}
