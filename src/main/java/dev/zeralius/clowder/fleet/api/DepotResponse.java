package dev.zeralius.clowder.fleet.api;

import dev.zeralius.clowder.common.geo.DistanceModel;
import java.math.BigDecimal;

/**
 * @param distanceModel which geometry the coordinates are in, and so how distance is measured
 * @param active a deactivated depot keeps its history and its plans, but takes no new ones
 */
public record DepotResponse(
        Long id,
        String code,
        String name,
        BigDecimal latitude,
        BigDecimal longitude,
        String timezone,
        DistanceModel distanceModel,
        boolean active) {}
