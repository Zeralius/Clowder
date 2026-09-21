package dev.zeralius.clowder.planning.model;

/**
 * A stop as the planner sees it. All times are whole minutes from the planning epoch — the service
 * date at midnight in the depot's zone. {@code Instant} arithmetic in the evaluator's inner loop
 * would be slower and harder to read; the conversion happens once, when specs are built.
 *
 * <p>Permits {@code windowEnd == windowStart}, where {@code TimeWindow} does not. {@code TimeWindow}
 * models a promise to a customer; this models an arithmetic constraint, and a degenerate constraint
 * is meaningful even though a degenerate promise is not.
 *
 * @param consignmentId the {@code consignment.id} this came from
 * @param index row in the {@link DistanceMatrix}; distinct from this stop's position in the instance,
 *     so two consignments at one address may share a row without the solver caring
 * @param windowStartMinutes earliest service start
 * @param windowEndMinutes latest service start — closed, so starting exactly at this minute is legal
 */
public record StopSpec(
        long consignmentId,
        int index,
        long weightGrams,
        long volumeMillilitres,
        int serviceMinutes,
        int windowStartMinutes,
        int windowEndMinutes) {

    public StopSpec {
        if (windowEndMinutes < windowStartMinutes) {
            throw new IllegalArgumentException(
                    "windowEnd %d is before windowStart %d".formatted(windowEndMinutes, windowStartMinutes));
        }
        if (serviceMinutes < 0) {
            throw new IllegalArgumentException("serviceMinutes must not be negative");
        }
    }
}
