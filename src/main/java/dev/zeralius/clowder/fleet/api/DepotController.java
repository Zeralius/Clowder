package dev.zeralius.clowder.fleet.api;

import dev.zeralius.clowder.common.web.PageResponse;
import dev.zeralius.clowder.fleet.domain.FleetService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Depends on {@code FleetService}, not {@code FleetFacade}: the facade is the cross-module contract,
 * and depot creation is not something another module should be able to do. A controller depending
 * on its own module's service is fine.
 *
 * <p>{@code DELETE} deactivates rather than deletes, the same verb as vehicles and drivers
 * (decision D3): a depot is referenced by plans and consignments that still have to be readable.
 */
@RestController
@RequestMapping("/api/depots")
public class DepotController {

    private final FleetService fleet;

    DepotController(FleetService fleet) {
        this.fleet = fleet;
    }

    @PostMapping
    ResponseEntity<DepotResponse> create(@Valid @RequestBody CreateDepotRequest request) {
        var created = fleet.createDepot(request);
        return ResponseEntity.created(URI.create("/api/depots/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    DepotResponse findById(@PathVariable Long id) {
        return fleet.findDepotById(id);
    }

    @GetMapping
    PageResponse<DepotResponse> findAll(@PageableDefault(size = 50) Pageable pageable) {
        return fleet.findAllDepots(pageable);
    }

    @PutMapping("/{id}")
    DepotResponse update(@PathVariable Long id, @Valid @RequestBody UpdateDepotRequest request) {
        return fleet.updateDepot(id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> deactivate(@PathVariable Long id) {
        fleet.deactivateDepot(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reactivate")
    DepotResponse reactivate(@PathVariable Long id) {
        return fleet.reactivateDepot(id);
    }
}
