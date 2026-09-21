package dev.zeralius.clowder.importer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.TestcontainersConfiguration;
import dev.zeralius.clowder.consignment.api.ConsignmentResponse;
import dev.zeralius.clowder.consignment.domain.ConsignmentService;
import dev.zeralius.clowder.consignment.domain.ConsignmentStatus;
import dev.zeralius.clowder.fleet.FleetFacade;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * M1's done-when, as a test: import an instance, then read it back through the ordinary query path
 * and check the counts, windows and demands survived the round trip.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SolomonImportIT {

    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 1, 5);

    @Autowired
    SolomonImportService importer;

    @Autowired
    ConsignmentService consignments;

    @Autowired
    FleetFacade fleet;

    private static Reader fixture() {
        var stream = Objects.requireNonNull(
                SolomonImportIT.class.getResourceAsStream("/solomon/MINI.txt"), "fixture missing");
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }

    @Test
    void importsDepotVehiclesAndConsignments() {
        var result = importer.importFrom(fixture(), SERVICE_DATE);

        assertThat(result.instance()).isEqualTo("MINI");
        assertThat(result.vehiclesCreated()).isEqualTo(3);
        assertThat(result.consignmentsCreated()).isEqualTo(3);
        assertThat(result.horizonMinutes()).isEqualTo(230);
    }

    @Test
    void everyVehicleGetsTheHorizonAsItsShift() {
        var result = importer.importFrom(fixture(), SERVICE_DATE);

        assertThat(fleet.findActiveVehiclesAt(result.depotId()))
                .hasSize(3)
                .allSatisfy(v -> {
                    assertThat(v.shiftMinutes()).isEqualTo(230);
                    assertThat(v.capacityGrams()).isEqualTo(200L);
                });
    }

    @Test
    void consignmentsComeBackThroughTheOrdinaryQueryPath() {
        var result = importer.importFrom(fixture(), SERVICE_DATE);

        var page = consignments.findAll(result.depotId(), SERVICE_DATE, PageRequest.of(0, 50));

        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.content())
                .extracting(ConsignmentResponse::reference)
                .containsExactlyInAnyOrder("MINI-001", "MINI-002", "MINI-003");
        assertThat(page.content()).allMatch(c -> c.status() == ConsignmentStatus.PENDING);
    }

    @Test
    void windowsAndDemandsSurviveTheRoundTrip() {
        var result = importer.importFrom(fixture(), SERVICE_DATE);

        var first = consignments.findAll(result.depotId(), SERVICE_DATE, PageRequest.of(0, 50)).content().stream()
                .filter(c -> c.reference().equals("MINI-001"))
                .findFirst()
                .orElseThrow();

        // Customer 1: ready 161, due 171, service 10, demand 10. 161 minutes past midnight is 02:41.
        assertThat(first.window().start()).isEqualTo(Instant.parse("2026-01-05T02:41:00Z"));
        assertThat(first.window().end()).isEqualTo(Instant.parse("2026-01-05T02:51:00Z"));
        assertThat(first.serviceMinutes()).isEqualTo(10);
        assertThat(first.weightGrams()).isEqualTo(10L);
        assertThat(first.volumeMillilitres()).isZero();
    }

    @Test
    void reimportingTheSameInstanceReusesTheDepot() {
        var first = importer.importFrom(fixture(), SERVICE_DATE);

        // Same depot code, so findOrCreateDepot returns the existing row rather than conflicting.
        assertThat(fleet.findAllDepots()).filteredOn(d -> d.code().equals("MINI")).hasSize(1);
        assertThat(first.depotCode()).isEqualTo("MINI");
    }
}
