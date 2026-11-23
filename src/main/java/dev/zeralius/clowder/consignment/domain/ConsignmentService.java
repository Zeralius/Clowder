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
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConsignmentService implements ConsignmentFacade {

    private static final Logger log = LoggerFactory.getLogger(ConsignmentService.class);

    /** What a planner may still put on a route. Dispatched means the driver already has it. */
    private static final Set<ConsignmentStatus> PLANNABLE =
            EnumSet.of(ConsignmentStatus.PENDING, ConsignmentStatus.PLANNED);

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
        return consignments.findByDepotIdAndServiceDateAndStatusIn(depotId, serviceDate, PLANNABLE).stream()
                .map(ConsignmentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void markPlanned(Collection<Long> consignmentIds) {
        advanceAll(consignmentIds, ConsignmentStatus.PLANNED);
    }

    @Override
    @Transactional
    public void markPending(Collection<Long> consignmentIds) {
        advanceAll(consignmentIds, ConsignmentStatus.PENDING);
    }

    @Override
    @Transactional
    public void markDispatched(Collection<Long> consignmentIds) {
        advanceAll(consignmentIds, ConsignmentStatus.DISPATCHED);
    }

    @Override
    @Transactional
    public void markDelivered(Long consignmentId) {
        advanceAll(List.of(consignmentId), ConsignmentStatus.DELIVERED);
    }

    @Override
    @Transactional
    public void markFailed(Long consignmentId) {
        advanceAll(List.of(consignmentId), ConsignmentStatus.FAILED);
    }

    private void advanceAll(Collection<Long> consignmentIds, ConsignmentStatus target) {
        for (final var consignment : consignments.findAllById(consignmentIds)) {
            advance(consignment, target);
        }
    }

    /**
     * Walks the state machine towards {@code target} rather than demanding a single legal hop.
     *
     * <p>The path is at most two steps in this lifecycle, so it is found by looking one state ahead
     * instead of with a graph search. A consignment already at the target, or stuck somewhere with no
     * way forward, is left exactly as it is: this is called from publishing a plan and from ingesting
     * driver events, and neither should fail because an order was cancelled an hour ago.
     */
    private void advance(Consignment consignment, ConsignmentStatus target) {
        if (consignment.getStatus() == target) {
            return;
        }
        if (consignment.getStatus().canTransitionTo(target)) {
            consignment.transitionTo(target);
            return;
        }
        for (final var step : consignment.getStatus().allowedTargets()) {
            if (step.canTransitionTo(target)) {
                consignment.transitionTo(step);
                consignment.transitionTo(target);
                return;
            }
        }
        log.debug("Leaving consignment {} at {}: no path to {}", consignment.getId(), consignment.getStatus(), target);
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
        var consignment = consignments.findById(id).orElseThrow(() -> new NotFoundException("Consignment", id));

        consignment.transitionTo(target);

        return ConsignmentMapper.toResponse(consignment);
    }
}
