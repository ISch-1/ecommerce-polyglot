package com.ecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Enforced by CI. Fails the build if any module reaches into another module's
 * internals. Also renders C4 and PlantUML docs into {@code target/spring-modulith-docs}.
 */
class ModularityTest {

    @Test
    void verifyModuleBoundaries() {
        ApplicationModules modules = ApplicationModules.of(EcommercePolyglotApplication.class);
        modules.verify();
    }

    @Test
    void writeDocumentation() {
        ApplicationModules modules = ApplicationModules.of(EcommercePolyglotApplication.class);
        new Documenter(modules)
                .writeDocumentation()
                .writeIndividualModulesAsPlantUml();
    }
}
