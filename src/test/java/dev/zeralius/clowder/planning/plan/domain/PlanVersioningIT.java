package dev.zeralius.clowder.planning.plan.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zeralius.clowder.TestcontainersConfiguration;
import dev.zeralius.clowder.common.error.ConflictException;
import dev.zeralius.clowder.common.error.NotFoundException;
import dev.zeralius.clowder.planning.plan.persistence.PlanRepository;
import dev.zeralius.clowder.planning.plan.persistence.PlanRunRepository;
import dev.zeralius.clowder.security.TestAuth;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/** Version 2 supersedes version 1. It never replaces it, and version 1 stays readable. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql("/db/testdata/plan-fixture.sql")
class PlanVersioningIT {

    private static final LocalDate DAY = LocalDate.of(2026, 1, 5);

    @Autowired
    PlanPublisher publisher;

    @Autowired
    PlanRepository plans;

    @Autowired
    PlanRunRepository runs;

    @Autowired
    JdbcTemplate jdbc;

    private Long vehicleId;

    @BeforeEach
    void readTheSeededVehicle() {
        // Publishing is attributed to the caller, so these tests need one. First line of the existing
        // setup rather than a second @BeforeEach, because JUnit does not define the order of those.
        TestAuth.asDispatcher();

        // Identity columns keep counting across the deletes in the fixture, so the id is looked up
        // rather than assumed to be 1.
        vehicleId = jdbc.queryForObject("select id from vehicle where registration = ?", Long.class, "PLAN-01");
    }

    @AfterEach
    void clearTheCaller() {
        TestAuth.clear();
    }

    private Long draft(int versionNo) {
        final var run = runs.save(new PlanRun(depotId(), DAY, "test", 1L, 1000));
        return plans.save(new Plan(run.getId(), depotId(), vehicleId, DAY, versionNo, 1_000L, 60))
                .getId();
    }

    private Long depotId() {
        return jdbc.queryForObject("select id from depot where code = ?", Long.class, "PLN");
    }

    @Test
    void publishingVersionTwoSupersedesVersionOne() {
        final var first = draft(1);
        final var second = draft(2);

        publisher.publish(first);
        publisher.publish(second);

        assertThat(plans.findById(first).orElseThrow().getStatus()).isEqualTo(PlanStatus.SUPERSEDED);
        assertThat(plans.findById(second).orElseThrow().getStatus()).isEqualTo(PlanStatus.PUBLISHED);
    }

    @Test
    void onlyOnePlanIsEverPublishedForAVehicleDay() {
        publisher.publish(draft(1));
        publisher.publish(draft(2));
        publisher.publish(draft(3));

        assertThat(plans.findAll().stream().filter(plan -> plan.getStatus() == PlanStatus.PUBLISHED))
                .hasSize(1);
    }

    @Test
    void nextVersionNumberFollowsTheHighestExisting() {
        draft(1);
        draft(2);

        assertThat(plans.findMaxVersionNo(vehicleId, DAY)).isEqualTo(2);
        assertThat(plans.findMaxVersionNo(vehicleId, DAY.plusDays(1))).isZero();
    }

    @Test
    void aSupersededPlanCannotBeRepublished() {
        final var first = draft(1);
        publisher.publish(first);
        publisher.publish(draft(2));

        assertThatThrownBy(() -> publisher.publish(first)).isInstanceOf(ConflictException.class);
    }

    @Test
    void aCancelledDraftCannotBePublished() {
        final var planId = draft(1);
        final var plan = plans.findById(planId).orElseThrow();
        plan.cancel();
        plans.saveAndFlush(plan);

        assertThatThrownBy(() -> publisher.publish(planId)).isInstanceOf(ConflictException.class);
    }

    @Test
    void publishingAnUnknownPlanIsNotFound() {
        assertThatThrownBy(() -> publisher.publish(999_999L)).isInstanceOf(NotFoundException.class);
    }

    /** The superseded version keeps its own version number and its stops; history is not rewritten. */
    @Test
    void thePreviousVersionIsStillReadableAfterwards() {
        final var first = draft(1);
        publisher.publish(first);
        publisher.publish(draft(2));

        final var superseded = plans.findById(first).orElseThrow();

        assertThat(superseded.getVersionNo()).isEqualTo(1);
        assertThat(superseded.getPublishedAt()).isNotNull();
        assertThat(superseded.getStatus()).isEqualTo(PlanStatus.SUPERSEDED);
    }

    /**
     * Attribution is part of the history, so a superseded version keeps the name of whoever published
     * it rather than the name of whoever replaced it. That is the point of recording it at all: the
     * question asked months later is who put that plan in front of a driver, not who last touched the
     * vehicle-day.
     */
    @Test
    void eachVersionKeepsThePublisherThatPublishedIt() {
        final var first = draft(1);
        final var second = draft(2);

        TestAuth.asDispatcher("morning-shift");
        publisher.publish(first);

        TestAuth.asDispatcher("afternoon-shift");
        publisher.publish(second);

        assertThat(plans.findById(first).orElseThrow().getPublishedBy()).isEqualTo("morning-shift");
        assertThat(plans.findById(second).orElseThrow().getPublishedBy()).isEqualTo("afternoon-shift");
    }

    @Test
    void aDraftHasNoPublisher() {
        assertThat(plans.findById(draft(1)).orElseThrow().getPublishedBy()).isNull();
    }

    /**
     * The guarantee is in the database and not only in the entity, same as the publish rule itself:
     * an UPDATE typed into psql cannot leave a published plan with nobody named on it either.
     */
    @Test
    void theDatabaseRefusesAPublishedRowWithNoPublisher() {
        final var planId = draft(1);

        assertThatThrownBy(() -> jdbc.update("update plan set status = 'PUBLISHED' where id = ?", planId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("plan_published_by_check");
    }
}
