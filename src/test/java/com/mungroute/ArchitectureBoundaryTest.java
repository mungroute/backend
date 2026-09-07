package com.mungroute;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.mungroute", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureBoundaryTest {
    @ArchTest
    static final ArchRule COURSE_CATALOG_USES_GROUP_ONLY_THROUGH_ITS_PORT = noClasses()
            .that().resideInAPackage("..course.catalog.service..")
            .should().dependOnClassesThat().resideInAnyPackage("..group.repository..", "..group.service..");

    @ArchTest
    static final ArchRule COURSE_MATCHING_DOES_NOT_DEPEND_ON_WALK = noClasses()
            .that().resideInAPackage("..course.matching..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..walk.domain..", "..walk.repository..", "..walk.service..");

    @ArchTest
    static final ArchRule USER_SERVICES_REVOKE_AUTH_THROUGH_A_PORT = noClasses()
            .that().resideInAPackage("..user.service..")
            .should().dependOnClassesThat().resideInAnyPackage("..auth.repository..", "..auth.service..");

    @ArchTest
    static final ArchRule WALK_SERVICES_USE_GROUP_MEET_AND_PROXIMITY_PORTS = noClasses()
            .that().resideInAPackage("..walk.service..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..group.repository..", "..group.service..",
                    "..meet.repository..", "..meet.service..",
                    "..proximity.repository..", "..proximity.service..", "..proximity.store..");
}
