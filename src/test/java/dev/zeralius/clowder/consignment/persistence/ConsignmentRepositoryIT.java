package dev.zeralius.clowder.consignment.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zeralius.clowder.TestcontainersConfiguration;
import dev.zeralius.clowder.common.geo.LatLon;
import dev.zeralius.clowder.common.time.TimeWindow;
import dev.zeralius.clowder.consignment.domain.Consignment;
import dev.zeralius.clowder.fleet.persistence.DepotRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

/**
 * Goes to the repository directly, not through {@code ConsignmentService}: the service's
 * {@code existsByReference} pre-check would fire first and prove nothing about the database. The
 * unique constraint is the actual guarantee; this is the test that it exists.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql("/db/testdata/depots.sql")
@Transactional
class ConsignmentRepositoryIT {

    @Autowired
    ConsignmentRepository consignments;

    @Autowired
    DepotRepository depots;

    private Consignment consignmentWithReference(String reference) {
        var depotId = depots.findByCode("MRL").orElseThrow().getId();
        return new Consignment(
                reference,
                depotId,
                LocalDate.of(2026, 1, 5),
                LatLon.of("51.65", "7.09"),
                "Beispielstrasse 1, Marl",
                1_000L,
                0L,
                5,
                new TimeWindow(Instant.parse("2026-01-05T09:00:00Z"), Instant.parse("2026-01-05T12:00:00Z")));
    }

    @Test
    void theDatabaseRejectsADuplicateReference() {
        consignments.saveAndFlush(consignmentWithReference("CON-DUP"));

        // saveAndFlush, not save: the insert has to reach Postgres inside the repository call for
        // Spring's exception translation to turn the constraint violation into this type.
        assertThatThrownBy(() -> consignments.saveAndFlush(consignmentWithReference("CON-DUP")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
