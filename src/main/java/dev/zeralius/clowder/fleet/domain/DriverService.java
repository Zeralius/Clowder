package dev.zeralius.clowder.fleet.domain;


import dev.zeralius.clowder.common.error.ConflictException;
import dev.zeralius.clowder.common.error.NotFoundException;
import dev.zeralius.clowder.common.web.PageResponse;
import dev.zeralius.clowder.fleet.api.CreateDriverRequest;
import dev.zeralius.clowder.fleet.api.DriverResponse;
import dev.zeralius.clowder.fleet.api.UpdateDriverRequest;
import dev.zeralius.clowder.fleet.persistence.DepotRepository;
import dev.zeralius.clowder.fleet.persistence.DriverRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class DriverService {

    private final DriverRepository drivers;
    private final DepotRepository depots;

    public DriverService(DriverRepository drivers, DepotRepository depots) {
        this.drivers = drivers;
        this.depots = depots;
    }

    public DriverResponse findById(Long id) {
        return drivers.findByIdWithDepot(id)
                .map(DriverMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("Driver", id));
    }

    public PageResponse<DriverResponse> findAll(Pageable pageable) {
        return PageResponse.from(drivers.findAll(pageable), DriverMapper::toResponse);
    }

    public List<DriverResponse> findActiveDriversAt(Long depotId) {
        if (!depots.existsById(depotId)) {
            throw new NotFoundException("Depot", depotId);
        }
        return drivers.findByDepotIdAndActiveTrue(depotId).stream()
                .map(DriverMapper::toResponse)
                .toList();
    }

    @Transactional
    public DriverResponse create(CreateDriverRequest request) {
        if(drivers.existsByEmployeeNo(request.employeeNo())) {
            throw new ConflictException(
                    "A driver with the employee number '%s' already exists".formatted(request.employeeNo())
            );
        }
        var depot = depots.findById(request.depotId())
                .orElseThrow(() -> new NotFoundException("Depot", request.depotId()));

        var driver = new Driver(depot, request.employeeNo(), request.fullName(), request.licenceClass());

        return DriverMapper.toResponse(drivers.save(driver));
    }

    @Transactional
    public DriverResponse update(Long id, UpdateDriverRequest request) {
        var driver = drivers.findByIdWithDepot(id).orElseThrow(() -> new NotFoundException("Driver", id));

        driver.rename(request.fullName());
        driver.updateLicenceClass(request.licenceClass());

        return DriverMapper.toResponse(driver);
    }

    @Transactional
    public void deactivate(Long id) {
        drivers.findById(id).orElseThrow(() -> new NotFoundException("Driver", id)).deactivate();
    }
}
