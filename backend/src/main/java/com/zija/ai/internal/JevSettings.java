package com.zija.ai.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Jev 决策路由试验配置。默认关闭；密钥仅来自环境变量，不入库。
 */
@Component
class JevSettings {

    private final boolean enabled;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;
    private final double minConfidence;

    JevSettings(
            @Value("${zija.ai.jev.enabled:false}") boolean enabled,
            @Value("${zija.ai.jev.api-key:}") String apiKey,
            @Value("${zija.ai.jev.base-url:https://openrouter.ai/api/alpha/decisions}") String baseUrl,
            @Value("${zija.ai.jev.model:typesafe/jev-1.13}") String model,
            @Value("${zija.ai.jev.timeout-seconds:8}") int timeoutSeconds,
            @Value("${zija.ai.jev.min-confidence:0.55}") double minConfidence
    ) {
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://openrouter.ai/api/alpha/decisions"
                : baseUrl.trim();
        this.model = model == null || model.isBlank() ? "typesafe/jev-1.13" : model.trim();
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
        this.minConfidence = minConfidence;
    }

    boolean enabled() {
        return enabled;
    }

    boolean configured() {
        return enabled && !apiKey.isBlank();
    }

    String apiKey() {
        return apiKey;
    }

    String baseUrl() {
        return baseUrl;
    }

    String model() {
        return model;
    }

    int timeoutSeconds() {
        return timeoutSeconds;
    }

    double minConfidence() {
        return minConfidence;
    }
}
