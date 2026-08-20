package com.zija.household.internal;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 生产环境必须配置初始化口令，防止公网暴露时匿名抢建家庭。
 */
@Component
@Profile("prod")
class HouseholdSetupTokenStartupValidator {

    @Value("${zija.setup.token:}")
    private String configuredToken;

    @PostConstruct
    void validateConfigured() {
        if (configuredToken == null || configuredToken.isBlank()) {
            throw new IllegalStateException(
                    "ZIJA_SETUP_TOKEN must be set when spring.profiles.active includes prod");
        }
    }
}
