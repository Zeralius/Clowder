package dev.zeralius.clowder.fleet.persistence;

import dev.zeralius.clowder.fleet.domain.Driver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    boolean existsByEmployeeNo(String employeeNo);

    @Query("select d from Driver d join fetch d.depot where d.id = :id")
    Optional<Driver> findByIdWithDepot(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = "depot")
    Page<Driver> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "depot")
    List<Driver> findByDepotIdAndActiveTrue(Long depotId);
}
