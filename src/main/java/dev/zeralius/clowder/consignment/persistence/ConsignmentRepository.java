package dev.zeralius.clowder.consignment.persistence;

import dev.zeralius.clowder.consignment.domain.Consignment;
import dev.zeralius.clowder.consignment.domain.ConsignmentStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsignmentRepository extends JpaRepository<Consignment, Long> {

    boolean existsByReference(String reference);

    Page<Consignment> findByServiceDate(LocalDate serviceDate, Pageable pageable);

    /** Spring Data binds by position, in the order the properties appear in the name. */
    Page<Consignment> findByDepotIdAndServiceDate(Long depotId, LocalDate serviceDate, Pageable pageable);

    /** The whole depot-day, whatever the status. M5 tracks stops that are no longer PENDING. */
    List<Consignment> findByDepotIdAndServiceDate(Long depotId, LocalDate serviceDate);

    /** Covered exactly by {@code idx_consignment_planning}. This is M3's hot query. */
    List<Consignment> findByDepotIdAndServiceDateAndStatus(
            Long depotId, LocalDate serviceDate, ConsignmentStatus status);
}
