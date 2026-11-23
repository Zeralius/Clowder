package dev.zeralius.clowder;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import dev.zeralius.clowder.planning.plan.domain.PlanRunStatus;
import dev.zeralius.clowder.planning.plan.domain.PlanStatus;
import jakarta.persistence.Entity;
import org.springframework.beans.factory.annotation.Autowired;

@AnalyzeClasses(packages = "dev.zeralius.clowder", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    @ArchTest
    static final ArchRule controllerDoNotTouchRepositories = noClasses()
            .that()
            .resideInAPackage("..api..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..persistence..")
            .because("controllers go through the module facade");

    @ArchTest
    static final ArchRule entitiesStayInsideTheirModule = classes()
            .that()
            .areAnnotatedWith(Entity.class)
            .should()
            .resideInAPackage("..domain..")
            .because("entities are internal to a module");

    @ArchTest
    static final ArchRule noFieldInjection =
            noFields().should().beAnnotatedWith(Autowired.class).because("constructor injection only");

    @ArchTest
    static final ArchRule noCycles =
            slices().matching("dev.zeralius.clowder.(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule commonDependsOnNothing = noClasses()
            .that()
            .resideInAPackage("..common..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..fleet..", "..consignment..", "..importer..", "..planning..", "..execution..")
            .because("common is the shared kernel and must stay dependency-free");

    @ArchTest
    static final ArchRule modulesDoNotShareEntities = noClasses()
            .that()
            .resideInAPackage("..consignment..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..fleet.domain..")
            .because("modules talk through facades and ids, never entities");

    /**
     * Only the security module touches Spring Security.
     *
     * <p>Without this the {@code CurrentUser} facade is a suggestion: any service could reach for
     * {@code SecurityContextHolder} and start making its own authorisation decisions, and then the
     * answer to "who may do this?" would be spread across every module that happened to care. The
     * rule is what keeps it in one place.
     */
    @ArchTest
    static final ArchRule onlySecurityKnowsAboutSpringSecurity = noClasses()
            .that()
            .resideOutsideOfPackage("..security..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.security..")
            .because("authorisation decisions go through the CurrentUser facade, not the context holder");

    /**
     * Execution gets plans as DTOs through {@code PlanningFacade}. The rule is what makes that a
     * boundary rather than a preference: fold an event log over a planning entity once and the two
     * modules are welded together, and the projector stops being testable without a database.
     *
     * <p>The two status enums are named exceptions, because they are values in the published
     * vocabulary rather than entities — {@code PlanResponse} carries one, exactly as
     * {@code ConsignmentResponse} carries {@code ConsignmentStatus}. Listing them here keeps the
     * exception visible; widening the rule to the whole package would hide the next real breach.
     */
    @ArchTest
    static final ArchRule executionReadsPlanningThroughItsFacade = noClasses()
            .that()
            .resideInAPackage("..execution..")
            .should()
            .dependOnClassesThat(resideInAnyPackage("..planning.plan.domain..", "..planning.plan.persistence..")
                    .and(not(equivalentTo(PlanStatus.class)))
                    .and(not(equivalentTo(PlanRunStatus.class))))
            .because("execution reads plans through the facade, as DTOs, never as entities");

    /**
     * Named packages, not {@code ..planning..}: M4 puts persistence and controllers under
     * {@code planning.plan}, and those are legitimately Spring beans. If this ever fails for a package
     * in the list, the fix is to move whatever needed the framework out into {@code planning.plan},
     * never to relax the rule.
     */
    @ArchTest
    static final ArchRule planningCoreIsFrameworkFree = noClasses()
            .that()
            .resideInAnyPackage(
                    "..planning.model..",
                    "..planning.feasibility..",
                    "..planning.solver..",
                    "..planning.bench..",
                    "..planning.render..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "jakarta.validation..")
            .because("the planning core is plain Java: no container, no ORM, testable without infrastructure");
}
