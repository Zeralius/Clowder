package dev.zeralius.clowder.planning.model;

/**
 * A vehicle as the planner sees it: capacities and a shift, no identity beyond an id.
 *
 * @param id the {@code vehicle.id} this came from, for mapping results back
 * @param capacityGrams weight capacity
 * @param capacityMillilitres volume capacity — inactive for Solomon instances (decision D4)
 * @param shiftStartMinutes minutes from the planning epoch at which the vehicle leaves the depot
 * @param shiftMinutes how long the shift lasts (decision D1: a length, not an end time)
 * @param depotIndex row in the {@link DistanceMatrix} for this vehicle's depot
 */
public record VehicleSpec(
        long id,
        long capacityGrams,
        long capacityMillilitres,
        int shiftStartMinutes,
        int shiftMinutes,
        int depotIndex) {

    public VehicleSpec {
        if (capacityGrams < 0 || capacityMillilitres < 0) {
            throw new IllegalArgumentException("capacities must not be negative");
        }
        if (shiftMinutes <= 0) {
            throw new IllegalArgumentException("shiftMinutes must be positive: " + shiftMinutes);
        }
    }

    /** Minutes from the planning epoch at which the vehicle must be back at the depot. */
    public int shiftEndMinutes() {
        return shiftStartMinutes + shiftMinutes;
    }
}
