package com.zija.ai.internal;

import java.util.concurrent.TimeUnit;

/** Enforces deployment-wide AI request limits without coupling the provider seam to a library. */
class AiRequestGuard {

    private static final long WINDOW_NANOS = TimeUnit.MINUTES.toNanos(1);

    private final Object monitor = new Object();
    private long windowStarted = System.nanoTime();
    private int requestCount;
    private int activeRequests;

    Permit acquire(AiProviderConfiguration configuration, int estimatedTokens) {
        if (estimatedTokens > configuration.maxContextTokens()) {
            throw new AiRequestLimitException("AI context limit exceeded");
        }
        synchronized (monitor) {
            long now = System.nanoTime();
            if (now - windowStarted >= WINDOW_NANOS) {
                windowStarted = now;
                requestCount = 0;
            }
            if (requestCount >= configuration.requestsPerMinute()) {
                throw new AiRequestLimitException("AI request rate limit exceeded");
            }
            if (activeRequests >= configuration.maxConcurrentRequests()) {
                throw new AiRequestLimitException("AI concurrency limit exceeded");
            }
            requestCount++;
            activeRequests++;
            return new Permit(this);
        }
    }

    private void release() {
        synchronized (monitor) {
            activeRequests--;
        }
    }

    static int estimateTokens(String text) {
        return Math.max(1, (text.length() + 3) / 4);
    }

    static final class Permit implements AutoCloseable {

        private final AiRequestGuard owner;
        private boolean released;

        private Permit(AiRequestGuard owner) {
            this.owner = owner;
        }

        @Override
        public void close() {
            if (!released) {
                released = true;
                owner.release();
            }
        }
    }
}
