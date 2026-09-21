package dev.zeralius.clowder.consignment.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The coordinate bounds and the cross-field window check both exist so a bad request is a 400 with
 * field-level detail, not a 500 from {@code LatLon} or {@code TimeWindow} refusing to construct.
 */
public record CreateConsignmentRequest(
        @NotBlank @Size(max = 32) String reference,
        @NotNull Long depotId,
        @NotNull LocalDate serviceDate,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        @NotBlank @Size(max = 256) String addressLine,
        @PositiveOrZero long weightGrams,
        @PositiveOrZero long volumeMillilitres,
        @PositiveOrZero int serviceMinutes,
        @NotNull Instant windowStart,
        @NotNull Instant windowEnd) {

    /** Hibernate Validator picks up {@code isXxx()} methods, so this reports as a field error. */
    @AssertTrue(message = "windowEnd must be after windowStart")
    private boolean isWindowOrdered() {
        return windowStart == null || windowEnd == null || windowEnd.isAfter(windowStart);
    }
}
