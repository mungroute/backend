package com.mungroute;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.mungroute", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureBoundaryTest {
    @ArchTest
    static final ArchRule USE_CASES_DO_NOT_REACH_FOREIGN_SERVICES_OR_REPOSITORIES = classes()
            .that().resideInAPackage("com.mungroute..service..")
            .should(new ArchCondition<>("depend only on their own feature's services and repositories") {
                @Override
                public void check(JavaClass source, ConditionEvents events) {
                    String sourceFeature = feature(source.getPackageName());
                    source.getDirectDependenciesFromSelf().stream()
                            .filter(dependency -> dependency.getTargetClass().getPackageName()
                                    .startsWith("com.mungroute."))
                            .filter(dependency -> {
                                String targetPackage = dependency.getTargetClass().getPackageName();
                                return targetPackage.contains(".service")
                                        || targetPackage.contains(".repository");
                            })
                            .filter(dependency -> !sourceFeature.equals(feature(
                                    dependency.getTargetClass().getPackageName())))
                            .forEach(dependency -> events.add(SimpleConditionEvent.violated(
                                    source,
                                    source.getName() + " directly depends on foreign implementation "
                                            + dependency.getTargetClass().getName()
                            )));
                }
            });

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

    @ArchTest
    static final ArchRule WALK_SERVICES_USE_USER_PORTS = noClasses()
            .that().resideInAPackage("..walk.service..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..user.repository..", "..user.service..");

    @ArchTest
    static final ArchRule MEET_AND_PROXIMITY_USE_WALK_SESSION_PORT = noClasses()
            .that().resideInAnyPackage("..meet.service..", "..proximity.service..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..walk.domain..", "..walk.repository..", "..walk.service..");

    private static String feature(String packageName) {
        String suffix = packageName.substring("com.mungroute.".length());
        int separator = suffix.indexOf('.');
        return separator < 0 ? suffix : suffix.substring(0, separator);
    }
}
