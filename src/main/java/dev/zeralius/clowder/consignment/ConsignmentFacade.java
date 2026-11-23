package dev.zeralius.clowder.consignment;

import dev.zeralius.clowder.consignment.api.ConsignmentResponse;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** What planning may know about consignments. Nothing else is exposed across the boundary. */
public interface ConsignmentFacade {

    /**
     * Everything at this depot on this date that may be put on a route: {@code PENDING} and
     * {@code PLANNED}. Planned ones are included so that replanning a published day works — the
     * dispatcher is rebuilding the routes those consignments are on. Once a consignment is dispatched
     * the driver is holding it, so it is no longer available to a planner.
     */
    List<ConsignmentResponse> findPlannableFor(Long depotId, LocalDate serviceDate);

    /**
     * Every consignment at this depot on this date, whatever its status. Execution needs the ones
     * already on a plan, which {@link #findPlannableFor} deliberately excludes.
     */
    List<ConsignmentResponse> findForServiceDate(Long depotId, LocalDate serviceDate);

    /**
     * Lifecycle, driven by planning and execution rather than by hand.
     *
     * <p>Each of these is idempotent and tolerant: a consignment already in the target state is left
     * alone, one in a terminal state is never dragged back out of it, and an intermediate step is
     * walked through rather than demanded. That last part matters because the event log is the truth
     * and it has gaps — a {@code DELIVERED} whose {@code DEPARTED_DEPOT} never arrived still means the
     * parcel was delivered, and refusing it because the paperwork is out of order would be absurd.
     */
    void markPlanned(Collection<Long> consignmentIds);

    /** Unplan: pulled off a route that was superseded and not replaced. */
    void markPending(Collection<Long> consignmentIds);

    void markDispatched(Collection<Long> consignmentIds);

    void markDelivered(Long consignmentId);

    void markFailed(Long consignmentId);
}
