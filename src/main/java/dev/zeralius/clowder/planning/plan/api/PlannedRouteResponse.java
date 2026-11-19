package dev.zeralius.clowder.planning.plan.api;

import java.util.List;

/**
 * One vehicle's route. Identified by id only: {@code VehicleSpec} carries no registration, and
 * inventing a lookup here would duplicate the snapshot M4's {@code plan_stop} is for.
 *
 * @param distance in the matrix's minor units (decision D7)
 * @param distanceUnits the same figure in whole units, for reading
 */
public record PlannedRouteResponse(
        Long vehicleId,
        int stopCount,
        long distance,
        double distanceUnits,
        int durationMinutes,
        List<PlannedStopResponse> stops) {}
