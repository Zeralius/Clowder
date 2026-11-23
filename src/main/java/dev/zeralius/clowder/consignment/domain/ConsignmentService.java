package dev.zeralius.clowder.consignment.domain;

import dev.zeralius.clowder.common.error.ConflictException;
import dev.zeralius.clowder.common.error.NotFoundException;
import dev.zeralius.clowder.common.geo.LatLon;
import dev.zeralius.clowder.common.time.TimeWindow;
import dev.zeralius.clowder.common.web.PageResponse;
import dev.zeralius.clowder.consignment.ConsignmentFacade;
import dev.zeralius.clowder.consignment.api.ConsignmentResponse;
import dev.zeralius.clowder.consignment.api.CreateConsignmentRequest;
import dev.zeralius.clowder.consignment.persistence.ConsignmentRepository;
import dev.zeralius.clowder.fleet.FleetFacade;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConsignmentService implements ConsignmentFacade {

    private final ConsignmentRepository consignments;
    private final FleetFacade fleet;

    public ConsignmentService(ConsignmentRepository consignments, FleetFacade fleet) {
        this.consignments = consignments;
        this.fleet = fleet;
    }

    public ConsignmentResponse findById(Long id) {
        return consignments
                .findById(id)
                .map(ConsignmentMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("Consignment", id));
    }

    public PageResponse<ConsignmentResponse> findAll(Long depotId, LocalDate serviceDate, Pageable pageable) {
        var page = depotId == null
                ? consignments.findByServiceDate(serviceDate, pageable)
                : consignments.findByDepotIdAndServiceDate(depotId, serviceDate, pageable);
        return PageResponse.from(page, ConsignmentMapper::toResponse);
    }

    @Override
    public List<ConsignmentResponse> findForServiceDate(Long depotId, LocalDate serviceDate) {
        return consignments.findByDepotIdAndServiceDate(depotId, serviceDate).stream()
                .map(ConsignmentMapper::toResponse)
                .toList();
    }

    @Override
    public List<ConsignmentResponse> findPlannableFor(Long depotId, LocalDate serviceDate) {
        return consignments
                .findByDepotIdAndServiceDateAndStatus(depotId, serviceDate, ConsignmentStatus.PENDING)
                .stream()
                .map(ConsignmentMapper::toResponse)
                .toList();
    }

    @Transactional
    public ConsignmentResponse create(CreateConsignmentRequest request) {
        if (consignments.existsByReference(request.reference())) {
            throw new ConflictException(
                    "A consignment with reference '%s' already exists".formatted(request.reference()));
        }

        // Through the facade, so an unknown depot is a 404 that names it rather than a foreign-key
        // violation. Scanning every depot is crude but keeps the facade narrow; when the depot count
        // stops being a handful, add depotExists(Long) to FleetFacade rather than reaching into
        // DepotRepository from this module.
        fleet.findAllDepots().stream()
                .filter(d -> d.id().equals(request.depotId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Depot", request.depotId()));

        var consignment = new Consignment(
                request.reference(),
                request.depotId(),
                request.serviceDate(),
                new LatLon(request.latitude(), request.longitude()),
                request.addressLine(),
                request.weightGrams(),
                request.volumeMillilitres(),
                request.serviceMinutes(),
                new TimeWindow(request.windowStart(), request.windowEnd()));

        return ConsignmentMapper.toResponse(consignments.save(consignment));
    }

    @Transactional
    public ConsignmentResponse transition(Long id, ConsignmentStatus target) {
        var consignment =
                consignments.findById(id).orElseThrow(() -> new NotFoundException("Consignment", id));

        consignment.transitionTo(target);

        return ConsignmentMapper.toResponse(consignment);
    }
}
