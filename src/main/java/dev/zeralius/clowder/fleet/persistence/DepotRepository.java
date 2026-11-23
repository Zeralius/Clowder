package dev.zeralius.clowder.fleet.persistence;

import dev.zeralius.clowder.fleet.domain.Depot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotRepository extends JpaRepository<Depot, Long> {
    Optional<Depot> findById(Long id);

    Optional<Depot> findByCode(String code);
}
