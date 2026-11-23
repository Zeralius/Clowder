package dev.zeralius.clowder.consignment;

import dev.zeralius.clowder.consignment.api.ConsignmentResponse;
import java.time.LocalDate;
import java.util.List;

/** What planning may know about consignments. Nothing else is exposed across the boundary. */
public interface ConsignmentFacade {

    /**
     * Everything at this depot on this date that is waiting to be put on a route. Today that means
     * {@code PENDING}; when M4 replans a published day it will also mean {@code PLANNED}.
     */
    List<ConsignmentResponse> findPlannableFor(Long depotId, LocalDate serviceDate);

    /**
     * Every consignment at this depot on this date, whatever its status. Execution needs the ones
     * already on a plan, which {@link #findPlannableFor} deliberately excludes.
     */
    List<ConsignmentResponse> findForServiceDate(Long depotId, LocalDate serviceDate);
}
