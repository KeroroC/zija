package com.zija;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class ModularityTests {

    private final ApplicationModules modules =
            ApplicationModules.of(ZijaApplication.class);

    @Test
    void systemModuleExistsAndDependenciesAreValid() {
        assertThat(modules.getModuleByName("system")).isPresent();
        modules.verify();
    }

    @Test
    void identityAndHouseholdModulesExist() {
        assertThat(modules.getModuleByName("identity")).isPresent();
        assertThat(modules.getModuleByName("household")).isPresent();
        modules.verify();
    }

    @Test
    void inventoryModuleExistsAndDependenciesAreValid() {
        assertThat(modules.getModuleByName("inventory")).isPresent();
        modules.verify();
    }
}
