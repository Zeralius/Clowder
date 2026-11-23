package dev.zeralius.clowder.fleet.api;

import dev.zeralius.clowder.common.web.PageResponse;
import dev.zeralius.clowder.fleet.domain.DriverService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final DriverService drivers;

    DriverController(DriverService drivers) {
        this.drivers = drivers;
    }

    @PostMapping
    ResponseEntity<DriverResponse> create(@Valid @RequestBody CreateDriverRequest request) {
        var created = drivers.create(request);
        return ResponseEntity.created(URI.create("/api/drivers/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    DriverResponse findById(@PathVariable Long id) {
        return drivers.findById(id);
    }

    @GetMapping
    PageResponse<DriverResponse> findAll(@PageableDefault(size = 50) Pageable pageable) {
        return drivers.findAll(pageable);
    }

    @PutMapping("/{id}")
    DriverResponse update(@PathVariable Long id, @Valid @RequestBody UpdateDriverRequest request) {
        return drivers.update(id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> deactivate(@PathVariable Long id) {
        drivers.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
