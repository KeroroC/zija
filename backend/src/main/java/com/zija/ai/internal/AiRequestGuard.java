package com.zija.ai.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

/** Enforces deployment-wide AI request limits without coupling the provider seam to a library. */
@Component
class AiRequestGuard {

    private static final long WINDOW_NANOS = TimeUnit.MINUTES.toNanos(1);

    private final Object monitor = new Object();
    private long windowStarted = System.nanoTime();
    private int requestCount;
    private int activeRequests;
    private final Map<UUID, Integer> memberRequestCounts = new HashMap<>();

    Permit acquire(AiProviderConfiguration configuration, int estimatedTokens) {
        return acquire(configuration, null, estimatedTokens);
    }

    Permit acquire(AiProviderConfiguration configuration, UUID accountId, int estimatedTokens) {
        if (estimatedTokens > configuration.maxContextTokens()) {
            throw new AiRequestLimitException("AI_CONTEXT_LIMIT_EXCEEDED", "AI context limit exceeded");
        }
        synchronized (monitor) {
            long now = System.nanoTime();
            if (now - windowStarted >= WINDOW_NANOS) {
                windowStarted = now;
                requestCount = 0;
                memberRequestCounts.clear();
            }
            if (requestCount >= configuration.requestsPerMinute()) {
                throw new AiRequestLimitException(
                        "AI_DEPLOYMENT_RATE_LIMITED", "AI request rate limit exceeded");
            }
            if (accountId != null
                    && memberRequestCounts.getOrDefault(accountId, 0)
                    >= configuration.memberRequestsPerMinute()) {
                throw new AiRequestLimitException(
                        "AI_MEMBER_RATE_LIMITED", "AI member request rate limit exceeded");
            }
            if (activeRequests >= configuration.maxConcurrentRequests()) {
                throw new AiRequestLimitException(
                        "AI_CONCURRENCY_LIMIT_EXCEEDED", "AI concurrency limit exceeded");
            }
            requestCount++;
            if (accountId != null) {
                memberRequestCounts.merge(accountId, 1, Integer::sum);
            }
            activeRequests++;
            return new Permit(this);
        }
    }

    private void release() {
        synchronized (monitor) {
            activeRequests = Math.max(0, activeRequests - 1);
        }
    }

    void reset() {
        synchronized (monitor) {
            windowStarted = System.nanoTime();
            requestCount = 0;
            activeRequests = 0;
            memberRequestCounts.clear();
        }
    }

    static int estimateTokens(String text) {
        int estimated = 0;
        int asciiRun = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint <= 0x7f) {
                asciiRun++;
            } else {
                estimated += (asciiRun + 3) / 4 + 1;
                asciiRun = 0;
            }
        }
        return Math.max(1, estimated + (asciiRun + 3) / 4);
    }

    static final class Permit implements AutoCloseable {

        private final AiRequestGuard owner;
        private final AtomicBoolean released = new AtomicBoolean();

        private Permit(AiRequestGuard owner) {
            this.owner = owner;
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                owner.release();
            }
        }
    }
}
