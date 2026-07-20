package com.zija.identity.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    @Test
    void allowsUntilAccountThreshold() {
        var limiter = new LoginRateLimiter(5, 5, 20, 5, 1000);
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.recordFailure("owner", "10.0.0.1").isBlocked()).isFalse();
        }
        assertThatThrownBy(() -> limiter.recordFailure("owner", "10.0.0.1"))
                .isInstanceOf(LoginRateLimitedException.class);
    }

    @Test
    void accountAndIpBucketsAreIndependent() {
        var limiter = new LoginRateLimiter(5, 5, 20, 5, 1000);
        for (int i = 0; i < 4; i++) limiter.recordFailure("owner", "10.0.0.1");
        limiter.recordFailure("owner", "10.0.0.2");
        assertThat(limiter.recordFailure("owner", "10.0.0.1").isBlocked()).isFalse();
        assertThatThrownBy(() -> limiter.recordFailure("owner", "10.0.0.1"))
                .isInstanceOf(LoginRateLimitedException.class);
    }

    @Test
    void successClearsAccountBucket() {
        var limiter = new LoginRateLimiter(5, 5, 20, 5, 1000);
        for (int i = 0; i < 4; i++) limiter.recordFailure("owner", "10.0.0.1");
        limiter.recordSuccess("owner");
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.recordFailure("owner", "10.0.0.1").isBlocked()).isFalse();
        }
    }

    @Test
    void unknownUsernameAlsoRateLimited() {
        var limiter = new LoginRateLimiter(5, 5, 20, 5, 1000);
        assertThatThrownBy(() -> {
            for (int i = 0; i < 30; i++) limiter.recordFailure("ghost", "10.0.0.1");
        }).isInstanceOf(LoginRateLimitedException.class);
    }
}
