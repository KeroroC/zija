package com.zija.identity.internal;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    @Test
    void fifthFailureLocksAccount() {
        var limiter = new LoginRateLimiter(5, 5, 20, 5, 1000);
        for (int i = 0; i < 4; i++) {
            assertThat(limiter.recordFailure("owner", "10.0.0.1").isBlocked()).isFalse();
        }
        assertThatThrownBy(() -> limiter.recordFailure("owner", "10.0.0.1"))
                .isInstanceOf(LoginRateLimitedException.class);
    }

    @Test
    void accountBucketAggregatesFailuresAcrossIps() {
        var limiter = new LoginRateLimiter(5, 5, 20, 5, 1000);
        for (int i = 1; i <= 4; i++) {
            limiter.recordFailure("owner", "10.0.0." + i);
        }

        assertThatThrownBy(() -> limiter.recordFailure("owner", "10.0.0.5"))
                .isInstanceOf(LoginRateLimitedException.class);
    }

    @Test
    void twentiethFailureLocksIpAcrossUsernames() {
        var limiter = new LoginRateLimiter(50, 5, 20, 5, 1000);
        for (int i = 0; i < 19; i++) {
            limiter.recordFailure("user-" + i, "10.0.0.1");
        }

        assertThatThrownBy(() -> limiter.recordFailure("user-20", "10.0.0.1"))
                .isInstanceOf(LoginRateLimitedException.class);
    }

    @Test
    void checkAllowedRejectsBlockedAccountBeforeAuthentication() {
        var limiter = new LoginRateLimiter(5, 5, 20, 5, 1000);
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("owner", "10.0.0.1");
        }
        assertThatThrownBy(() -> limiter.recordFailure("owner", "10.0.0.1"))
                .isInstanceOf(LoginRateLimitedException.class);

        assertThatThrownBy(() -> limiter.checkAllowed("owner", "10.0.0.99"))
                .isInstanceOf(LoginRateLimitedException.class);
    }

    @Test
    void lockExpiresFiveMinutesAfterThreshold() {
        var clock = new MutableClock(Instant.parse("2026-07-21T00:00:00Z"));
        var limiter = new LoginRateLimiter(5, 5, 20, 5, 1000, clock);
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("owner", "10.0.0.1");
        }
        assertThatThrownBy(() -> limiter.recordFailure("owner", "10.0.0.1"))
                .isInstanceOf(LoginRateLimitedException.class);

        clock.advance(Duration.ofMinutes(5));

        limiter.checkAllowed("owner", "10.0.0.1");
        assertThat(limiter.recordFailure("owner", "10.0.0.1").isBlocked()).isFalse();
    }

    @Test
    void successClearsAccountBucket() {
        var limiter = new LoginRateLimiter(5, 5, 20, 5, 1000);
        for (int i = 0; i < 4; i++) limiter.recordFailure("owner", "10.0.0.1");
        limiter.recordSuccess("owner");
        for (int i = 0; i < 4; i++) {
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

    @Test
    void expiredBucketsAreRemovedDuringCleanup() {
        var clock = new MutableClock(Instant.parse("2026-07-21T00:00:00Z"));
        var limiter = new LoginRateLimiter(5, 5, 20, 5, 3, clock);
        limiter.recordFailure("first", "10.0.0.1");
        limiter.recordFailure("second", "10.0.0.2");
        limiter.recordFailure("third", "10.0.0.3");

        clock.advance(Duration.ofMinutes(6));
        limiter.recordFailure("fourth", "10.0.0.4");

        assertThat(limiter.accountBucketCount()).isEqualTo(1);
    }

    @Test
    void capacityPressureDoesNotEvictBlockedAccountFirst() {
        var limiter = new LoginRateLimiter(2, 5, 20, 5, 3);
        limiter.recordFailure("owner", "10.0.0.1");
        assertThatThrownBy(() -> limiter.recordFailure("owner", "10.0.0.1"))
                .isInstanceOf(LoginRateLimitedException.class);

        limiter.recordFailure("unknown-1", "10.0.0.2");
        limiter.recordFailure("unknown-2", "10.0.0.3");
        limiter.recordFailure("unknown-3", "10.0.0.4");

        assertThatThrownBy(() -> limiter.checkAllowed("owner", "10.0.0.99"))
                .isInstanceOf(LoginRateLimitedException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
