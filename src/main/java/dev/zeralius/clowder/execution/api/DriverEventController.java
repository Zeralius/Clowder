package dev.zeralius.clowder.execution.api;

import dev.zeralius.clowder.execution.domain.EventIngestionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plans/{planId}/events")
public class DriverEventController {

    private final EventIngestionService ingestion;

    DriverEventController(EventIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    /**
     * Always 200, never 201 and never 409 for a replay. A retry is a successful call that returns the
     * original result: a client on a bad connection cannot tell "you told me already" from "thanks",
     * and should not have to.
     */
    @PostMapping
    EventResponse record(@PathVariable Long planId, @Valid @RequestBody RecordEventRequest request) {
        return ingestion.record(planId, request);
    }
}
