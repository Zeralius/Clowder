package dev.zeralius.clowder.fleet.api;

import dev.zeralius.clowder.common.geo.DistanceModel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * No {@code code}: it is how imports and operators identify a depot, and renaming an identifier is a
 * different operation from correcting the details of the thing it names. The same reasoning keeps
 * {@code registration} out of {@link UpdateVehicleRequest}.
 */
public record UpdateDepotRequest(
        @NotBlank @Size(max = 128) String name,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        @NotBlank @Size(max = 64) String timezone,
        @NotNull DistanceModel distanceModel) {}
