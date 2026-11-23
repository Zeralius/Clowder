package dev.zeralius.clowder.fleet.api;

import dev.zeralius.clowder.common.geo.DistanceModel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * @param distanceModel optional: a hand-created depot is real geography, so leaving it out means
 *     {@link DistanceModel#HAVERSINE}. The Solomon importer says {@code EUCLIDEAN} explicitly,
 *     because its coordinates are plane units and measuring them on a sphere would make the
 *     benchmark results incomparable
 */
public record CreateDepotRequest(
        @NotBlank @Size(max = 16) String code,
        @NotBlank @Size(max = 128) String name,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        @NotBlank @Size(max = 64) String timezone,
        DistanceModel distanceModel) {

    public DistanceModel distanceModelOrDefault() {
        return distanceModel == null ? DistanceModel.HAVERSINE : distanceModel;
    }
}
