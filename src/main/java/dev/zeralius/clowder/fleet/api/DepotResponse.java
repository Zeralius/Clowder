package dev.zeralius.clowder.fleet.api;

import java.math.BigDecimal;

public record DepotResponse(
        Long id,
        String code,
        String name,
        BigDecimal latitude,
        BigDecimal longitude,
        String timezone
) {

}
