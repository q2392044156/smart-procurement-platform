package com.procuremind.platform;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.procuremind.platform")
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllersDoNotExecuteSql = noClasses()
            .that().resideInAPackage("..controller..")
            .and().doNotHaveSimpleName("LegacyErpController")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");

    @ArchTest
    static final ArchRule servicesDoNotExecuteSql = noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");

    @ArchTest
    static final ArchRule servicesDoNotDependOnControllers = noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule filtersDoNotDependOnControllers = noClasses()
            .that().resideInAPackage("..filter..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule pojoStaysIndependent = noClasses()
            .that().resideInAPackage("..pojo..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..controller..", "..service..", "..filter..", "..aop..");
}
