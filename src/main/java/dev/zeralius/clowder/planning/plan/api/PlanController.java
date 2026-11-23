package dev.zeralius.clowder.planning.plan.api;

import dev.zeralius.clowder.planning.plan.domain.PlanPublisher;
import dev.zeralius.clowder.planning.plan.domain.PlanQueryService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanQueryService queries;
    private final PlanPublisher publisher;

    PlanController(PlanQueryService queries, PlanPublisher publisher) {
        this.queries = queries;
        this.publisher = publisher;
    }

    @GetMapping("/{id}")
    PlanResponse findById(@PathVariable Long id) {
        return queries.findPlan(id);
    }

    /** Every version for a vehicle-day, newest first. */
    @GetMapping
    List<PlanResponse> findForVehicleDay(
            @RequestParam Long vehicleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate) {
        return queries.findVersions(vehicleId, serviceDate);
    }

    /** Publishing is a transition, so it is a sub-resource rather than a PATCH on a status field. */
    @PostMapping("/{id}/publish")
    PlanResponse publish(@PathVariable Long id) {
        return publisher.publish(id);
    }
}
