package com.zija.ai.internal;

/** Provider settings passed only inside the AI module. */
record AiProviderConfiguration(
        String providerId,
        String credential,
        boolean outboundEnabled,
        int requestsPerMinute,
        int memberRequestsPerMinute,
        int maxContextTokens,
        int maxConcurrentRequests,
        int requestTimeoutSeconds
) {
}
