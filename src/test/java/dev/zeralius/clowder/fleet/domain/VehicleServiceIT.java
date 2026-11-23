package dev.zeralius.clowder.fleet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zeralius.clowder.TestcontainersConfiguration;
import dev.zeralius.clowder.common.error.ConflictException;
import dev.zeralius.clowder.common.error.NotFoundException;
import dev.zeralius.clowder.fleet.api.CreateVehicleRequest;
import dev.zeralius.clowder.fleet.api.UpdateVehicleRequest;
import dev.zeralius.clowder.fleet.api.VehicleResponse;
import dev.zeralius.clowder.fleet.persistence.DepotRepository;
import java.math.BigDecimal;
import java.time.LocalTime;
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
class VehicleServiceIT {

    @Autowired
    VehicleService vehicles;

    @Autowired
    DepotRepository depots;

    private Long depotId;

    @BeforeEach
    void resolveSeededDepot() {
        depotId = depots.findByCode("MRL").orElseThrow().getId();
    }

    private CreateVehicleRequest newVehicle(String registration) {
        return new CreateVehicleRequest(
                depotId, registration, 1_200_000L, 8_000_000L, LocalTime.of(6, 0), 540, new BigDecimal("45.00"));
    }

    @Test
    void createsAVehicleAtItsDepot() {
        var created = vehicles.create(newVehicle("MRL-AA-01"));

        assertThat(created.id()).isNotNull();
        assertThat(created.registration()).isEqualTo("MRL-AA-01");
        assertThat(created.depot().code()).isEqualTo("MRL");
        assertThat(created.active()).isTrue();
    }

    @Test
    void rejectsADuplicateRegistrationWithAConflict() {
        vehicles.create(newVehicle("MRL-AA-01"));

        assertThatThrownBy(() -> vehicles.create(newVehicle("MRL-AA-01")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("MRL-AA-01");
    }

    @Test
    void rejectsAnUnknownDepotWithANotFound() {
        var orphan = new CreateVehicleRequest(
                999_999L, "MRL-AA-02", 1_200_000L, 8_000_000L, LocalTime.of(6, 0), 540, new BigDecimal("45.00"));

        assertThatThrownBy(() -> vehicles.create(orphan))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Depot");
    }

    @Test
    void findByIdReturnsTheVehicleWithItsDepotSummary() {
        var id = vehicles.create(newVehicle("MRL-AA-03")).id();

        assertThat(vehicles.findById(id).depot().code()).isEqualTo("MRL");
    }

    @Test
    void findByIdThrowsForAnUnknownId() {
        assertThatThrownBy(() -> vehicles.findById(999_999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Vehicle");
    }

    @Test
    void updateChangesCapacityShiftAndSpeedButNotRegistration() {
        var id = vehicles.create(newVehicle("MRL-AA-04")).id();

        var updated = vehicles.update(
                id, new UpdateVehicleRequest(900_000L, 6_000_000L, LocalTime.of(7, 30), 480, new BigDecimal("38.50")));

        assertThat(updated.capacityGrams()).isEqualTo(900_000L);
        assertThat(updated.capacityMillilitres()).isEqualTo(6_000_000L);
        assertThat(updated.shiftStart()).isEqualTo(LocalTime.of(7, 30));
        assertThat(updated.shiftMinutes()).isEqualTo(480);
        assertThat(updated.avgSpeedKph()).isEqualByComparingTo("38.50");
        assertThat(updated.registration()).isEqualTo("MRL-AA-04");
    }

    @Test
    void deactivateFlipsTheFlagInsteadOfDeletingTheRow() {
        var id = vehicles.create(newVehicle("MRL-AA-05")).id();

        vehicles.deactivate(id);

        assertThat(vehicles.findById(id).active()).isFalse();
    }

    @Test
    void listingIsPaginated() {
        vehicles.create(newVehicle("MRL-AA-06"));
        vehicles.create(newVehicle("MRL-AA-07"));
        vehicles.create(newVehicle("MRL-AA-08"));

        var page = vehicles.findAll(PageRequest.of(0, 2));

        assertThat(page.content()).hasSize(2);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void findActiveVehiclesAtSkipsDeactivatedOnes() {
        vehicles.create(newVehicle("MRL-AA-09"));
        var retired = vehicles.create(newVehicle("MRL-AA-10")).id();
        vehicles.deactivate(retired);

        assertThat(vehicles.findActiveVehiclesAt(depotId))
                .extracting(VehicleResponse::registration)
                .containsExactly("MRL-AA-09");
    }

    @Test
    void findActiveVehiclesAtThrowsForAnUnknownDepot() {
        assertThatThrownBy(() -> vehicles.findActiveVehiclesAt(999_999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Depot");
    }
}
