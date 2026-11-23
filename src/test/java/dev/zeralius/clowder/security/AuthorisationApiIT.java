package dev.zeralius.clowder.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.TestcontainersConfiguration;
import dev.zeralius.clowder.execution.PublishedPlanFixture;
import dev.zeralius.clowder.execution.api.RecordEventRequest;
import dev.zeralius.clowder.execution.domain.DriverEventType;
import dev.zeralius.clowder.fleet.FleetFacade;
import dev.zeralius.clowder.planning.plan.api.AssignDriverRequest;
import dev.zeralius.clowder.planning.plan.api.PlanResponse;
import dev.zeralius.clowder.planning.plan.api.PlanRunRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Who may do what, over HTTP, against a real published plan.
 *
 * <p>Two kinds of rule are checked here and they are enforced in different places. Role rules live
 * in the filter chain and are about the URL. The ownership rule — a driver may only touch a plan
 * assigned to them — cannot be: the plan id is in the path and whose plan it is lives in the
 * database, so it is enforced in the service and shows up as a 403 rather than a 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, PublishedPlanFixture.class, TestTokens.class})
@Sql("/db/testdata/depots.sql")
class AuthorisationApiIT {

    @LocalServerPort
    int port;

    @Autowired
    TestTokens tokens;

    @Autowired
    PublishedPlanFixture fixture;

    @Autowired
    FleetFacade fleet;

    @Autowired
    JdbcTemplate jdbc;

    private RestTestClient rest;
    private PlanResponse plan;
    private Long ourDriver;
    private Long someoneElse;

    @BeforeEach
    void publishAPlanAndCreateTwoDrivers() {
        rest = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

        TestAuth.asDispatcher();
        plan = fixture.publishedPlan();

        ourDriver = insertDriver("DRV-001", "Ours");
        someoneElse = insertDriver("DRV-002", "Theirs");
    }

    @AfterEach
    void clearTheCaller() {
        TestAuth.clear();
    }

    /** Through SQL rather than the API, because the roster is not what these tests are about. */
    private Long insertDriver(String employeeNo, String name) {
        jdbc.update(
                "insert into driver (depot_id, employee_no, full_name, licence_class, active) "
                        + "values (?, ?, ?, 'C', true)",
                plan.depotId(),
                employeeNo,
                name);
        return jdbc.queryForObject("select id from driver where employee_no = ?", Long.class, employeeNo);
    }

    private void assignTo(Long driverId) {
        rest.post()
                .uri("/api/plans/" + plan.id() + "/driver")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForDispatcher())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AssignDriverRequest(driverId))
                .exchange()
                .expectStatus()
                .isOk();
    }

    private RestTestClient.ResponseSpec getPlanAs(String bearer) {
        return rest.get()
                .uri("/api/plans/" + plan.id())
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .exchange();
    }

    private RestTestClient.ResponseSpec postEventAs(String bearer, String key) {
        return rest.post()
                .uri("/api/plans/" + plan.id() + "/events")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RecordEventRequest(
                        key,
                        plan.stops().getFirst().id(),
                        DriverEventType.DELIVERED,
                        plan.stops().getFirst().plannedDeparture(),
                        null,
                        null,
                        null))
                .exchange();
    }

    // --- role rules, from the filter chain ------------------------------------------------------

    @Test
    void aDriverMayNotPlanADepotDay() {
        rest.post()
                .uri("/api/plan-runs")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForDriver(ourDriver))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PlanRunRequest(plan.depotId(), LocalDate.of(2026, 1, 5), 2, null))
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    @Test
    void aDriverMayNotPublishAPlan() {
        rest.post()
                .uri("/api/plans/" + plan.id() + "/publish")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForDriver(ourDriver))
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    @Test
    void aDriverMayNotCreateADepot() {
        rest.post()
                .uri("/api/depots")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForDriver(ourDriver))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new dev.zeralius.clowder.fleet.api.CreateDepotRequest(
                        "NOPE", "Not Allowed", new BigDecimal("51.0"), new BigDecimal("7.0"), "UTC", null))
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    /** A dispatcher plans; reference data is somebody else's job. */
    @Test
    void aDispatcherMayNotCreateADepotButMayReadOne() {
        rest.post()
                .uri("/api/depots")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForDispatcher())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new dev.zeralius.clowder.fleet.api.CreateDepotRequest(
                        "NOPE", "Not Allowed", new BigDecimal("51.0"), new BigDecimal("7.0"), "UTC", null))
                .exchange()
                .expectStatus()
                .isForbidden();

        rest.get()
                .uri("/api/depots/" + plan.depotId())
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForDispatcher())
                .exchange()
                .expectStatus()
                .isOk();
    }

    /** A driver needs to see the depot they are leaving from, so reads are open to any role. */
    @Test
    void aDriverMayReadReferenceData() {
        rest.get()
                .uri("/api/depots/" + plan.depotId())
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForDriver(ourDriver))
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void onlyAnAdminMayManageUsers() {
        rest.get()
                .uri("/api/users")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForDispatcher())
                .exchange()
                .expectStatus()
                .isForbidden();

        rest.get()
                .uri("/api/users")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForAdmin())
                .exchange()
                .expectStatus()
                .isOk();
    }

    // --- the ownership rule, from the service ---------------------------------------------------

    @Test
    void anUnassignedPlanBelongsToNoDriver() {
        assertThat(plan.driverId()).isNull();

        getPlanAs(tokens.bearerForDriver(ourDriver)).expectStatus().isForbidden();
        postEventAs(tokens.bearerForDriver(ourDriver), "k1").expectStatus().isForbidden();
    }

    @Test
    void theAssignedDriverMayReadThePlanAndReportAgainstIt() {
        assignTo(ourDriver);

        getPlanAs(tokens.bearerForDriver(ourDriver)).expectStatus().isOk();
        postEventAs(tokens.bearerForDriver(ourDriver), "k1").expectStatus().isOk();
    }

    @Test
    void anotherDriverMayNotTouchIt() {
        assignTo(ourDriver);

        getPlanAs(tokens.bearerForDriver(someoneElse)).expectStatus().isForbidden();
        postEventAs(tokens.bearerForDriver(someoneElse), "k1").expectStatus().isForbidden();
    }

    /** A dispatcher entering events for a driver whose phone died is normal work, not a workaround. */
    @Test
    void aDispatcherMayReportOnAnyPlan() {
        assignTo(ourDriver);

        postEventAs(tokens.bearerForDispatcher(), "k1").expectStatus().isOk();
        getPlanAs(tokens.bearerForDispatcher()).expectStatus().isOk();
    }

    @Test
    void reassigningMovesAccessWithTheRoster() {
        assignTo(ourDriver);
        getPlanAs(tokens.bearerForDriver(ourDriver)).expectStatus().isOk();

        assignTo(someoneElse);

        getPlanAs(tokens.bearerForDriver(someoneElse)).expectStatus().isOk();
        getPlanAs(tokens.bearerForDriver(ourDriver)).expectStatus().isForbidden();
    }

    /** Tracking goes through the same plan read, so it inherits the same rule. */
    @Test
    void trackingFollowsTheSameOwnershipRule() {
        assignTo(ourDriver);

        rest.get()
                .uri("/api/plans/" + plan.id() + "/tracking")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForDriver(ourDriver))
                .exchange()
                .expectStatus()
                .isOk();

        rest.get()
                .uri("/api/plans/" + plan.id() + "/tracking")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForDriver(someoneElse))
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    /**
     * The version list is filtered rather than refused: a vehicle-day may hold versions that were
     * someone else's before a reassignment, and a 403 for the whole list would be useless.
     */
    @Test
    void theVersionListIsFilteredForADriverRatherThanRefused() {
        assignTo(ourDriver);

        final var ours = rest.get()
                .uri("/api/plans?vehicleId=" + plan.vehicleId() + "&serviceDate=" + plan.serviceDate())
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForDriver(ourDriver))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PlanResponse[].class)
                .returnResult()
                .getResponseBody();

        assertThat(ours).hasSize(1);

        final var theirs = rest.get()
                .uri("/api/plans?vehicleId=" + plan.vehicleId() + "&serviceDate=" + plan.serviceDate())
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForDriver(someoneElse))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PlanResponse[].class)
                .returnResult()
                .getResponseBody();

        assertThat(theirs).isEmpty();
    }

    /** A 403 says what was refused without saying whose it is. */
    @Test
    void theRefusalDoesNotNameTheDriverItBelongsTo() {
        assignTo(ourDriver);

        final var body = rest.get()
                .uri("/api/plans/" + plan.id())
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerForDriver(someoneElse))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.FORBIDDEN)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).contains("not assigned to you").doesNotContain(String.valueOf(ourDriver));
    }
}
