package dev.zeralius.clowder.consignment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zeralius.clowder.TestcontainersConfiguration;
import dev.zeralius.clowder.common.error.ConflictException;
import dev.zeralius.clowder.common.error.NotFoundException;
import dev.zeralius.clowder.consignment.api.ConsignmentResponse;
import dev.zeralius.clowder.consignment.api.CreateConsignmentRequest;
import dev.zeralius.clowder.fleet.persistence.DepotRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql("/db/testdata/depots.sql")
@Transactional
class ConsignmentServiceIT {

    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 1, 5);
    private static final Instant WINDOW_START = Instant.parse("2026-01-05T09:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("2026-01-05T12:00:00Z");

    @Autowired
    ConsignmentService consignments;

    @Autowired
    DepotRepository depots;

    private Long depotId;

    @BeforeEach
    void resolveSeededDepot() {
        depotId = depots.findByCode("MRL").orElseThrow().getId();
    }

    private CreateConsignmentRequest newConsignment(String reference) {
        return new CreateConsignmentRequest(
                reference,
                depotId,
                SERVICE_DATE,
                new BigDecimal("51.65"),
                new BigDecimal("7.09"),
                "Beispielstrasse 1, Marl",
                12_000L,
                40_000L,
                10,
                WINDOW_START,
                WINDOW_END);
    }

    @Test
    void createsAConsignmentInPendingState() {
        var created = consignments.create(newConsignment("CON-0001"));

        assertThat(created.status()).isEqualTo(ConsignmentStatus.PENDING);
        assertThat(created.depotId()).isEqualTo(depotId);
        assertThat(created.window().start()).isEqualTo(WINDOW_START);
        assertThat(created.window().end()).isEqualTo(WINDOW_END);
    }

    @Test
    void normalisesCoordinateScaleOnTheWayOut() {
        var created = consignments.create(newConsignment("CON-0002"));

        assertThat(created.location().latitude()).isEqualByComparingTo("51.650000");
    }

    @Test
    void rejectsADuplicateReference() {
        consignments.create(newConsignment("CON-0003"));

        assertThatThrownBy(() -> consignments.create(newConsignment("CON-0003")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("CON-0003");
    }

    @Test
    void rejectsAnUnknownDepot() {
        var orphan = new CreateConsignmentRequest(
                "CON-0004",
                999_999L,
                SERVICE_DATE,
                new BigDecimal("51.65"),
                new BigDecimal("7.09"),
                "Nowhere",
                1L,
                1L,
                1,
                WINDOW_START,
                WINDOW_END);

        assertThatThrownBy(() -> consignments.create(orphan))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Depot");
    }

    @Test
    void movesThroughTheLifecycle() {
        var id = consignments.create(newConsignment("CON-0005")).id();

        assertThat(consignments.transition(id, ConsignmentStatus.PLANNED).status())
                .isEqualTo(ConsignmentStatus.PLANNED);
        assertThat(consignments.transition(id, ConsignmentStatus.DISPATCHED).status())
                .isEqualTo(ConsignmentStatus.DISPATCHED);
        assertThat(consignments.transition(id, ConsignmentStatus.DELIVERED).status())
                .isEqualTo(ConsignmentStatus.DELIVERED);
    }

    @Test
    void refusesAnIllegalTransitionWithAConflict() {
        var id = consignments.create(newConsignment("CON-0006")).id();

        assertThatThrownBy(() -> consignments.transition(id, ConsignmentStatus.DELIVERED))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void findPlannableForReturnsOnlyPendingWork() {
        consignments.create(newConsignment("CON-0007"));
        var planned = consignments.create(newConsignment("CON-0008")).id();
        consignments.transition(planned, ConsignmentStatus.PLANNED);

        assertThat(consignments.findPlannableFor(depotId, SERVICE_DATE))
                .extracting(ConsignmentResponse::reference)
                .containsExactly("CON-0007");
    }

    @Test
    void listingIsFilteredByServiceDateAndPaginated() {
        consignments.create(newConsignment("CON-0009"));
        consignments.create(newConsignment("CON-0010"));

        var sameDay = consignments.findAll(depotId, SERVICE_DATE, PageRequest.of(0, 1));
        var otherDay = consignments.findAll(depotId, SERVICE_DATE.plusDays(1), PageRequest.of(0, 10));

        assertThat(sameDay.content()).hasSize(1);
        assertThat(sameDay.totalElements()).isEqualTo(2);
        assertThat(otherDay.content()).isEmpty();
    }
}
