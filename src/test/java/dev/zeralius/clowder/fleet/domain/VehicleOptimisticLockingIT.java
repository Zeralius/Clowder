package dev.zeralius.clowder.fleet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zeralius.clowder.TestcontainersConfiguration;
import dev.zeralius.clowder.fleet.api.CreateVehicleRequest;
import dev.zeralius.clowder.fleet.persistence.DepotRepository;
import dev.zeralius.clowder.fleet.persistence.VehicleRepository;
import java.math.BigDecimal;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.jdbc.Sql;

/**
 * Rehearsal for M4's concurrency test. Deliberately <em>not</em> {@code @Transactional}: inside one
 * transaction the persistence context is shared, both loads return the same managed instance, and
 * there is nothing to conflict. Each repository call here runs in its own short transaction, so the
 * two loads are genuinely separate copies of the same row.
 *
 * <p>The seed script clears {@code vehicle} before every test, which is what makes a committing test
 * safe to rerun.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql("/db/testdata/depots.sql")
class VehicleOptimisticLockingIT {

    @Autowired
    VehicleService vehicles;

    @Autowired
    VehicleRepository repository;

    @Autowired
    DepotRepository depots;

    @Test
    void theSecondOfTwoConcurrentEditsIsRejected() {
        var depotId = depots.findByCode("MRL").orElseThrow().getId();
        var id = vehicles.create(new CreateVehicleRequest(
                        depotId, "MRL-LOCK-01", 1_000_000L, 1_000_000L, LocalTime.of(6, 0), 540, new BigDecimal("45.00")))
                .id();

        // Two loads, two transactions, two detached instances at version 0.
        var first = repository.findById(id).orElseThrow();
        var second = repository.findById(id).orElseThrow();
        assertThat(first).isNotSameAs(second);

        first.updateAvgSpeed(new BigDecimal("50.00"));
        repository.save(first); // version 0 -> 1, committed

        second.updateAvgSpeed(new BigDecimal("55.00"));
        assertThatThrownBy(() -> repository.save(second)) // still claims version 0
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(vehicles.findById(id).avgSpeedKph()).isEqualByComparingTo("50.00");
    }
}
