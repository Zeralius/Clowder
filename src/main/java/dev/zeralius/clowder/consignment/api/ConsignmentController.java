package dev.zeralius.clowder.consignment.api;

import dev.zeralius.clowder.common.web.PageResponse;
import dev.zeralius.clowder.consignment.domain.ConsignmentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/consignments")
public class ConsignmentController {

    private final ConsignmentService consignments;

    ConsignmentController(ConsignmentService consignments) {
        this.consignments = consignments;
    }

    @PostMapping
    ResponseEntity<ConsignmentResponse> create(@Valid @RequestBody CreateConsignmentRequest request) {
        var created = consignments.create(request);
        return ResponseEntity.created(URI.create("/api/consignments/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    ConsignmentResponse findById(@PathVariable Long id) {
        return consignments.findById(id);
    }

    /** {@code serviceDate} is required: an unfiltered list of every consignment ever is not a use case. */
    @GetMapping
    PageResponse<ConsignmentResponse> findAll(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
            @RequestParam(required = false) Long depotId,
            @PageableDefault(size = 50) Pageable pageable) {
        return consignments.findAll(depotId, serviceDate, pageable);
    }

    /**
     * A transition is an action, not a field edit, so it gets its own sub-resource rather than a PATCH
     * on {@code status}. An illegal move returns 409 from the entity itself.
     */
    @PostMapping("/{id}/transitions")
    ConsignmentResponse transition(@PathVariable Long id, @Valid @RequestBody TransitionRequest request) {
        return consignments.transition(id, request.target());
    }
}
