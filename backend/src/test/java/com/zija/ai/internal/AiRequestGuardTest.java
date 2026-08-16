package com.zija.ai.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRequestGuardTest {

    @Test
    void rejectsRequestsBeyondTheConfiguredContextLimit() {
        var guard = new AiRequestGuard();

        assertThatThrownBy(() -> guard.acquire(configuration(10, 256, 2), 257))
                .isInstanceOf(AiRequestLimitException.class)
                .hasMessage("AI context limit exceeded");
    }

    @Test
    void rejectsRequestsBeyondTheConfiguredRateLimit() {
        var guard = new AiRequestGuard();
        try (var ignored = guard.acquire(configuration(1, 1024, 2), 1)) {
            // Holding the permit is not required for rate limiting, but mirrors a real call.
        }

        assertThatThrownBy(() -> guard.acquire(configuration(1, 1024, 2), 1))
                .isInstanceOf(AiRequestLimitException.class)
                .hasMessage("AI request rate limit exceeded");
    }

    @Test
    void rejectsRequestsBeyondTheConfiguredConcurrencyLimit() {
        var guard = new AiRequestGuard();
        try (var ignored = guard.acquire(configuration(10, 1024, 1), 1)) {
            assertThatThrownBy(() -> guard.acquire(configuration(10, 1024, 1), 1))
                    .isInstanceOf(AiRequestLimitException.class)
                    .hasMessage("AI concurrency limit exceeded");
        }
    }

    private AiProviderConfiguration configuration(int rate, int context, int concurrency) {
        return new AiProviderConfiguration("deterministic", null, false, rate, context, concurrency, 1);
    }
}
