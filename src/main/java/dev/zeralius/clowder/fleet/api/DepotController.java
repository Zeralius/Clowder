package dev.zeralius.clowder.fleet.api;

import dev.zeralius.clowder.fleet.FleetFacade;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/depots")
public class DepotController {

    private final FleetFacade fleet;

    DepotController(FleetFacade fleet) {
        this.fleet = fleet;
    }

    @GetMapping
    List<DepotResponse> findAll() {
        return fleet.findAllDepots();
    }
}
