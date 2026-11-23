package dev.zeralius.clowder.fleet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.TestcontainersConfiguration;
import dev.zeralius.clowder.fleet.api.CreateVehicleRequest;
import dev.zeralius.clowder.fleet.persistence.DepotRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

/**
 * The N+1 guard for the one place it was designed out: {@code VehicleRepository.findAll(Pageable)}
 * carries {@code @EntityGraph(attributePaths = "depot")}, so mapping fifty vehicles must not issue
 * fifty depot lookups.
 *
 * <p>Hibernate statistics are off by default and cost something, so they are switched on for this
 * context only. That gives this class its own Spring context; worth it for the one number.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(TestcontainersConfiguration.class)
@Sql("/db/testdata/depots.sql")
@Transactional
class VehicleListingQueryCountIT {

    private static final int PAGE_SIZE = 50;

    @Autowired
    VehicleService vehicles;

    @Autowired
    DepotRepository depots;

    @Autowired
    EntityManager entityManager;

    @Test
    void listingAPageOfVehiclesIsTwoStatementsNotFiftyOne() {
        var depotId = depots.findByCode("MRL").orElseThrow().getId();
        IntStream.rangeClosed(1, PAGE_SIZE)
                .forEach(i -> vehicles.create(new CreateVehicleRequest(
                        depotId,
                        "MRL-Q-%02d".formatted(i),
                        1_000_000L,
                        1_000_000L,
                        LocalTime.of(6, 0),
                        540,
                        new BigDecimal("45.00"))));

        // Everything just created is still in the persistence context. Flush and clear so the listing
        // has to go to the database for vehicles and depots alike, or the count is meaningless.
        entityManager.flush();
        entityManager.clear();

        var statistics = statistics();
        statistics.clear();

        var page = vehicles.findAll(PageRequest.of(0, PAGE_SIZE));

        assertThat(page.content()).hasSize(PAGE_SIZE);
        // One select with the depot join-fetched, plus the count query for totalElements.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    private Statistics statistics() {
        return entityManager
                .getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
    }
}
