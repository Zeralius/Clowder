package dev.zeralius.clowder.execution.api;

import dev.zeralius.clowder.execution.domain.DivergenceService;
import java.time.Clock;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plans/{planId}/tracking")
public class TrackingController {

    private final DivergenceService divergence;
    private final Clock clock;

    /**
     * A {@link Clock} is injected rather than calling {@code Instant.now()} inside the service. That
     * is the difference between a divergence report with tests and one you can only check by waiting
     * until the afternoon.
     */
    TrackingController(DivergenceService divergence, Clock clock) {
        this.divergence = divergence;
        this.clock = clock;
    }

    @GetMapping
    TrackingResponse track(@PathVariable Long planId) {
        return divergence.track(planId, clock.instant());
    }
}
