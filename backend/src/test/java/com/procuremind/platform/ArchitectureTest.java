package com.procuremind.platform;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.procuremind.platform")
class ArchitectureTest {
    @ArchTest
    static final ArchRule requisitionDoesNotReachOrderOrInventoryInternals = noClasses()
            .that().resideInAPackage("..requisition..")
            .should().dependOnClassesThat().resideInAnyPackage("..purchaseorder..", "..inventory..");

    @ArchTest
    static final ArchRule inventoryDoesNotReachRequisitionInternals = noClasses()
            .that().resideInAPackage("..inventory..")
            .should().dependOnClassesThat().resideInAPackage("..requisition..");
}
