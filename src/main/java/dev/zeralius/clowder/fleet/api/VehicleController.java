package dev.zeralius.clowder.fleet.api;


import dev.zeralius.clowder.common.web.PageResponse;
import dev.zeralius.clowder.fleet.domain.Vehicle;
import dev.zeralius.clowder.fleet.domain.VehicleService;
import dev.zeralius.clowder.fleet.persistence.VehicleRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicles;

    VehicleController(VehicleService vehicles) {
        this.vehicles = vehicles;
    }

    @PostMapping
    ResponseEntity<VehicleResponse> create(@Valid @RequestBody CreateVehicleRequest request) {
        var created = vehicles.create(request);
        return ResponseEntity.created(URI.create("/api/vehicles/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    VehicleResponse findById(@PathVariable Long id) {
        return vehicles.findById(id);
    }

    @GetMapping
    PageResponse<VehicleResponse> findAll(@PageableDefault(size = 50) Pageable pageable) {
        return vehicles.findAll(pageable);
    }

    @PutMapping("/{id}")
    VehicleResponse update(@PathVariable Long id, @Valid @RequestBody UpdateVehicleRequest request) {
        return vehicles.update(id, request);
    }


    /** Deactivates rather than deletes; a vehicle referenced by a published plan must survive. */
    @DeleteMapping("/{id}")
    ResponseEntity<Void> deactivate(@PathVariable Long id) {
        vehicles.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
