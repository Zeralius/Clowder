package dev.zeralius.clowder.fleet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zeralius.clowder.TestcontainersConfiguration;
import dev.zeralius.clowder.common.error.ConflictException;
import dev.zeralius.clowder.common.error.NotFoundException;
import dev.zeralius.clowder.fleet.api.CreateDriverRequest;
import dev.zeralius.clowder.fleet.api.UpdateDriverRequest;
import dev.zeralius.clowder.fleet.persistence.DepotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thinner than {@code VehicleServiceIT} on purpose. Vehicle is the template and earns the exhaustive
 * matrix; Driver is a copy of it, so this only proves the copy was made correctly: happy path, the
 * two error shapes, and the immutability the DTO claims.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql("/db/testdata/depots.sql")
@Transactional
class DriverServiceIT {

    @Autowired
    DriverService drivers;

    @Autowired
    DepotRepository depots;

    private Long depotId;

    @BeforeEach
    void resolveSeededDepot() {
        depotId = depots.findByCode("MRL").orElseThrow().getId();
    }

    private CreateDriverRequest newDriver(String employeeNo) {
        return new CreateDriverRequest(depotId, employeeNo, "Sam Rivers", "C1E");
    }

    @Test
    void createsADriverAtItsDepot() {
        var created = drivers.create(newDriver("EMP-001"));

        assertThat(created.id()).isNotNull();
        assertThat(created.employeeNo()).isEqualTo("EMP-001");
        assertThat(created.depot().code()).isEqualTo("MRL");
        assertThat(created.active()).isTrue();
    }

    @Test
    void rejectsADuplicateEmployeeNumber() {
        drivers.create(newDriver("EMP-001"));

        assertThatThrownBy(() -> drivers.create(newDriver("EMP-001")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("EMP-001");
    }

    @Test
    void rejectsAnUnknownDepot() {
        assertThatThrownBy(() -> drivers.create(new CreateDriverRequest(999_999L, "EMP-002", "Sam Rivers", "C1E")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Depot");
    }

    @Test
    void findByIdThrowsForAnUnknownId() {
        assertThatThrownBy(() -> drivers.findById(999_999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Driver");
    }

    @Test
    void updateChangesNameAndLicenceButNotEmployeeNumber() {
        var id = drivers.create(newDriver("EMP-003")).id();

        var updated = drivers.update(id, new UpdateDriverRequest("Sam Rivers-Okafor", "CE"));

        assertThat(updated.fullName()).isEqualTo("Sam Rivers-Okafor");
        assertThat(updated.licenceClass()).isEqualTo("CE");
        assertThat(updated.employeeNo()).isEqualTo("EMP-003");
    }

    @Test
    void deactivateRemovesADriverFromTheActiveList() {
        var id = drivers.create(newDriver("EMP-004")).id();

        drivers.deactivate(id);

        assertThat(drivers.findActiveDriversAt(depotId)).isEmpty();
        assertThat(drivers.findById(id).active()).isFalse();
    }
}
