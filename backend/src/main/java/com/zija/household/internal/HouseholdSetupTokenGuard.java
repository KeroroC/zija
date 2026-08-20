package com.zija.household.internal;

import com.zija.household.internal.exception.InvalidSetupTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 校验家庭初始化口令。未配置 {@code zija.setup.token} 时不启用校验（本地开发）。
 */
@Component
class HouseholdSetupTokenGuard {

    static final String HEADER_NAME = "X-Zija-Setup-Token";

    private final String configuredToken;

    HouseholdSetupTokenGuard(@Value("${zija.setup.token:}") String configuredToken) {
        this.configuredToken = configuredToken == null ? "" : configuredToken.trim();
    }

    boolean isRequired() {
        return !configuredToken.isEmpty();
    }

    void requireValid(String provided) {
        if (!isRequired()) {
            return;
        }
        if (provided == null || provided.isBlank()) {
            throw new InvalidSetupTokenException();
        }
        if (!constantTimeEquals(configuredToken, provided.trim())) {
            throw new InvalidSetupTokenException();
        }
    }

    private static boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
