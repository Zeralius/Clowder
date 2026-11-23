package dev.zeralius.clowder.planning.plan.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * One invocation of the solver, carrying everything needed to reproduce its output.
 *
 * <p>The seed and the solver version are two columns that answer "why did it route it that way in
 * March?" months later. Without them a published plan is an assertion; with them it is reproducible.
 */
@Entity
@Table(name = "plan_run")
public class PlanRun {

    private static final int MAX_FAILURE_REASON = 512;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "depot_id", nullable = false)
    private Long depotId;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PlanRunStatus status = PlanRunStatus.RUNNING;

    @Column(name = "solver_version", nullable = false, length = 64)
    private String solverVersion;

    @Column(nullable = false)
    private long seed;

    @Column(name = "budget_ms", nullable = false)
    private int budgetMs;

    @Column(name = "elapsed_ms")
    private Long elapsedMs;

    @Column(name = "iterations")
    private Integer iterations;

    @Column(name = "failure_reason", length = MAX_FAILURE_REASON)
    private String failureReason;

    @Column(name = "started_at", nullable = false, insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "plan_run_unassigned",
            joinColumns = @JoinColumn(name = "plan_run_id", nullable = false))
    private List<UnassignedConsignment> unassigned = new ArrayList<>();

    /** For JPA. */
    protected PlanRun() {}

    public PlanRun(Long depotId, LocalDate serviceDate, String solverVersion, long seed, int budgetMs) {
        this.depotId = depotId;
        this.serviceDate = serviceDate;
        this.solverVersion = solverVersion;
        this.seed = seed;
        this.budgetMs = budgetMs;
        this.status = PlanRunStatus.RUNNING;
    }

    public void succeed(long elapsedMs, int iterations, Collection<UnassignedConsignment> couldNotRoute) {
        this.status = PlanRunStatus.SUCCEEDED;
        this.elapsedMs = elapsedMs;
        this.iterations = iterations;
        this.unassigned.clear();
        this.unassigned.addAll(couldNotRoute);
        this.finishedAt = Instant.now();
    }

    public void fail(String reason) {
        this.status = PlanRunStatus.FAILED;
        this.failureReason = truncate(reason);
        this.finishedAt = Instant.now();
    }

    /** An exception message can exceed the column. Truncating beats a 500 on the failure path. */
    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= MAX_FAILURE_REASON ? reason : reason.substring(0, MAX_FAILURE_REASON);
    }

    public Long getId() {
        return id;
    }

    public Long getDepotId() {
        return depotId;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public PlanRunStatus getStatus() {
        return status;
    }

    public String getSolverVersion() {
        return solverVersion;
    }

    public long getSeed() {
        return seed;
    }

    public int getBudgetMs() {
        return budgetMs;
    }

    public Long getElapsedMs() {
        return elapsedMs;
    }

    public Integer getIterations() {
        return iterations;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public List<UnassignedConsignment> getUnassigned() {
        return Collections.unmodifiableList(unassigned);
    }
}
