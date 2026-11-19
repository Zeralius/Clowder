package dev.zeralius.clowder.planning.plan.api;

import java.time.LocalDate;
import java.util.List;

/**
 * The result of one solve. Nothing is persisted yet: M4 adds {@code plan_run}, versioned plans and
 * the publish rule, and this becomes the 202-plus-Location shape the tracker asks for.
 *
 * @param unassignedReferences consignments no vehicle could take — empty in a complete plan
 * @param solverVersion bump-on-change identifier, stored on {@code plan_run} in M4
 */
public record PlanRunResponse(
        Long depotId,
        LocalDate serviceDate,
        int consignmentCount,
        int vehiclesUsed,
        int vehiclesAvailable,
        long totalDistance,
        double totalDistanceUnits,
        List<String> unassignedReferences,
        long elapsedMillis,
        int iterations,
        long seed,
        String solverVersion,
        List<PlannedRouteResponse> routes) {}
