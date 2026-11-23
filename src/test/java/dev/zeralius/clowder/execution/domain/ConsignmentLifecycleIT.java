package dev.zeralius.clowder.execution.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.TestcontainersConfiguration;
import dev.zeralius.clowder.consignment.ConsignmentFacade;
import dev.zeralius.clowder.consignment.api.ConsignmentResponse;
import dev.zeralius.clowder.consignment.domain.ConsignmentStatus;
import dev.zeralius.clowder.execution.PublishedPlanFixture;
import dev.zeralius.clowder.execution.api.RecordEventRequest;
import dev.zeralius.clowder.planning.plan.api.PlanResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

/**
 * The consignment state machine has had these transitions since M1 and nothing drove them: a plan was
 * published and the orders on it stayed PENDING, a parcel was delivered and the order never knew. This
 * is the wiring, end to end.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, PublishedPlanFixture.class})
@Sql("/db/testdata/depots.sql")
class ConsignmentLifecycleIT {

    @Autowired
    EventIngestionService ingestion;

    @Autowired
    ConsignmentFacade consignments;

    @Autowired
    PublishedPlanFixture fixture;

    private PlanResponse plan;

    @BeforeEach
    void publishAPlan() {
        plan = fixture.publishedPlan();
    }

    private Map<Long, ConsignmentStatus> statuses() {
        return consignments.findForServiceDate(plan.depotId(), plan.serviceDate()).stream()
                .collect(Collectors.toMap(ConsignmentResponse::id, ConsignmentResponse::status));
    }

    private ConsignmentStatus statusOf(int stopIndex) {
        return statuses().get(plan.stops().get(stopIndex).consignmentId());
    }

    private void event(String key, Integer stopIndex, DriverEventType type, FailureReason reason) {
        ingestion.record(
                plan.id(),
                new RecordEventRequest(
                        key,
                        stopIndex == null ? null : plan.stops().get(stopIndex).id(),
                        type,
                        Instant.parse("2026-01-05T09:00:00Z"),
                        null,
                        null,
                        reason));
    }

    /** The fixture publishes, so every consignment on the plan is already planned by the time we look. */
    @Test
    void publishingAPlanMovesItsConsignmentsToPlanned() {
        assertThat(statuses().values()).allMatch(status -> status == ConsignmentStatus.PLANNED);
    }

    @Test
    void leavingTheDepotDispatchesEverythingOnTheRoute() {
        event("depart", null, DriverEventType.DEPARTED_DEPOT, null);

        assertThat(statuses().values()).allMatch(status -> status == ConsignmentStatus.DISPATCHED);
    }

    @Test
    void aDeliveredEventDeliversThatConsignmentAndNoOther() {
        event("depart", null, DriverEventType.DEPARTED_DEPOT, null);
        event("deliver", 0, DriverEventType.DELIVERED, null);

        assertThat(statusOf(0)).isEqualTo(ConsignmentStatus.DELIVERED);
        assertThat(statusOf(1)).isEqualTo(ConsignmentStatus.DISPATCHED);
        assertThat(statusOf(2)).isEqualTo(ConsignmentStatus.DISPATCHED);
    }

    @Test
    void aFailedEventFailsThatConsignmentWithoutBeingAnError() {
        event("depart", null, DriverEventType.DEPARTED_DEPOT, null);
        event("fail", 1, DriverEventType.FAILED, FailureReason.CUSTOMER_ABSENT);

        assertThat(statusOf(1)).isEqualTo(ConsignmentStatus.FAILED);
    }

    /**
     * The gap that matters: a delivery whose DEPARTED_DEPOT never arrived. The parcel was still
     * delivered, so the order is walked through DISPATCHED rather than the report being refused.
     */
    @Test
    void aDeliveryWithNoDepartureEventStillDeliversTheConsignment() {
        event("deliver", 0, DriverEventType.DELIVERED, null);

        assertThat(statusOf(0)).isEqualTo(ConsignmentStatus.DELIVERED);
    }

    /** An ARRIVED says where the van is, not what became of the parcel. */
    @Test
    void anArrivedEventLeavesTheOrderAlone() {
        event("depart", null, DriverEventType.DEPARTED_DEPOT, null);
        event("arrive", 0, DriverEventType.ARRIVED, null);

        assertThat(statusOf(0)).isEqualTo(ConsignmentStatus.DISPATCHED);
    }

    /** Replaying an event replays its consequence too, which has to be a no-op. */
    @Test
    void replayingADeliveryDoesNotMoveTheOrderTwice() {
        event("deliver", 0, DriverEventType.DELIVERED, null);
        event("deliver", 0, DriverEventType.DELIVERED, null);

        assertThat(statusOf(0)).isEqualTo(ConsignmentStatus.DELIVERED);
    }

    /**
     * And the reason planned work stays plannable: a dispatcher replanning a published day is
     * rebuilding the routes those consignments are on, so they must still be visible to the planner.
     */
    @Test
    void plannedConsignmentsAreStillAvailableToReplan() {
        final var plannable = consignments.findPlannableFor(plan.depotId(), plan.serviceDate());

        assertThat(plannable).hasSize(3);
        assertThat(plannable).extracting(ConsignmentResponse::status).containsOnly(ConsignmentStatus.PLANNED);
    }

    /** Once the driver has them, a planner cannot take them back. */
    @Test
    void dispatchedConsignmentsDropOutOfThePlannablePool() {
        event("depart", null, DriverEventType.DEPARTED_DEPOT, null);

        assertThat(consignments.findPlannableFor(plan.depotId(), plan.serviceDate()))
                .isEmpty();
    }

    @Test
    void theWholeRunEndsWithEveryOrderAccountedFor() {
        event("depart", null, DriverEventType.DEPARTED_DEPOT, null);
        event("d0", 0, DriverEventType.DELIVERED, null);
        event("d1", 1, DriverEventType.DELIVERED, null);
        event("f2", 2, DriverEventType.FAILED, FailureReason.REFUSED);
        event("home", null, DriverEventType.RETURNED_DEPOT, null);

        assertThat(List.of(statusOf(0), statusOf(1), statusOf(2)))
                .containsExactly(ConsignmentStatus.DELIVERED, ConsignmentStatus.DELIVERED, ConsignmentStatus.FAILED);
    }
}
