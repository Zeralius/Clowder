package dev.zeralius.clowder;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.beans.factory.annotation.Autowired;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(
        packages = "dev.zeralius.clowder",
        importOptions = ImportOption.DoNotIncludeTests.class
)

public class ArchitectureTest {

     @ArchTest
    static final ArchRule controllerDoNotTouchRepositories =
             noClasses().that().resideInAPackage("..api..")
             .should().dependOnClassesThat().resideInAPackage("..persistence..")
             .because("controllers go through the module facade");

     @ArchTest
    static final ArchRule entitiesStayInsideTheirModule =
             classes().that().areAnnotatedWith(Entity.class)
             .should().resideInAPackage("..domain..")
             .because("entities are internal to a module");

     @ArchTest
    static final ArchRule noFieldInjection =
             noFields().should().beAnnotatedWith(Autowired.class)
                     .because("constructor injection only");

     @ArchTest
    static final ArchRule noCycles =
             slices().matching("dev.zeralius.clowder.(*)..")
                     .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule commonDependsOnNothing =
            noClasses().that().resideInAPackage("..common..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..fleet..", "..consignment..", "..planning..", "..execution..")
                    .because("common is the shared kernel and must stay dependency-free");

    @ArchTest
    static final ArchRule modulesDoNotShareEntities =
            noClasses().that().resideInAPackage("..consignment..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..fleet.domain..")
                    .because("modules talk through facades and ids, never entities");
}
