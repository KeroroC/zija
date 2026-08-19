package com.zija.ai.internal;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** Applies the configured request budget to the real household Q&A model calls. */
@Component
class AiQaExecutionGuard {

    private final AiRequestGuard requestGuard;
    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("zija-ai-qa-", 0).factory());

    AiQaExecutionGuard(AiRequestGuard requestGuard) {
        this.requestGuard = requestGuard;
    }

    <T> T invoke(UUID accountId, AiService.QaSession session, String context, Supplier<T> call) {
        var configuration = session.configuration();
        var permit = requestGuard.acquire(
                configuration, accountId, AiRequestGuard.estimateTokens(context));
        try {
            return AiTimedCall.execute(
                    executor, call, permit, configuration.requestTimeoutSeconds());
        } catch (TimeoutException exception) {
            throw new AiProviderUnavailableException("AI_QA_TIMEOUT", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderUnavailableException("AI_QA_INTERRUPTED", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof AiRequestLimitException limit) throw limit;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new AiProviderUnavailableException("AI_QA_FAILED", cause);
        }
    }

    void checkContext(AiService.QaSession session, String context) {
        var configuration = session.configuration();
        if (AiRequestGuard.estimateTokens(context) > configuration.maxContextTokens()) {
            throw new AiRequestLimitException(
                    "AI_CONTEXT_LIMIT_EXCEEDED", "AI context limit exceeded");
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
