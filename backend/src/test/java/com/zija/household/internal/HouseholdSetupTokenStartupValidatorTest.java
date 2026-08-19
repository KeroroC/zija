package com.zija.household.internal;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HouseholdSetupTokenStartupValidatorTest {

    @Test
    void failsWhenConfiguredTokenBlank() {
        var validator = new HouseholdSetupTokenStartupValidator();
        ReflectionTestUtils.setField(validator, "configuredToken", "   ");

        assertThatThrownBy(validator::validateConfigured)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ZIJA_SETUP_TOKEN must be set when spring.profiles.active includes prod");
    }

    @Test
    void passesWhenConfiguredTokenPresent() {
        var validator = new HouseholdSetupTokenStartupValidator();
        ReflectionTestUtils.setField(validator, "configuredToken", "setup-secret");

        assertThatCode(validator::validateConfigured).doesNotThrowAnyException();
    }
}
