package com.zija.ai.internal;

import com.zija.ai.AiApi;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Service
class AiService implements AiApi {

    static final int EMBEDDING_DIMENSIONS = 768;

    private final AiSettingsService settingsService;
    private final List<AiModelProvider> providers;
    private final AiRequestGuard requestGuard = new AiRequestGuard();
    private final ExecutorService providerExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "zija-ai-provider");
        thread.setDaemon(true);
        return thread;
    });

    AiService(AiSettingsService settingsService, List<AiModelProvider> providers) {
        this.settingsService = settingsService;
        this.providers = providers;
    }

    @PreDestroy
    void shutdownProviderExecutor() {
        providerExecutor.shutdownNow();
    }

    @Override
    public String providerId() {
        return settingsService.currentConfiguration().providerId();
    }

    @Override
    public ChatReply complete(ChatRequest request) {
        var configuration = settingsService.currentConfiguration();
        var provider = requireProvider(configuration);
        var providerConfiguration = toProviderConfiguration(configuration);
        return invoke(() -> provider.complete(request, providerConfiguration), providerConfiguration,
                AiRequestGuard.estimateTokens(request.prompt()));
    }

    @Override
    public EmbeddingReply embed(EmbeddingRequest request) {
        var configuration = settingsService.currentConfiguration();
        var provider = requireProvider(configuration);
        var providerConfiguration = toProviderConfiguration(configuration);
        var estimatedTokens = request.inputs().stream().mapToInt(AiRequestGuard::estimateTokens).sum();
        var reply = invoke(() -> provider.embed(request, providerConfiguration), providerConfiguration,
                estimatedTokens);
        if (reply.vectors().size() != request.inputs().size()
                || reply.vectors().stream().anyMatch(vector -> vector == null || vector.length != EMBEDDING_DIMENSIONS)) {
            throw new AiProviderUnavailableException("provider returned invalid embedding dimensions");
        }
        return reply;
    }

    @Override
    public AiStatus status() {
        var configuration = settingsService.currentConfiguration();
        if (!configuration.enabled()) {
            return status(configuration, false, "AI_DISABLED", "AI is disabled", null, null);
        }
        var selection = selectProvider(configuration);
        if (selection.provider() == null) {
            return status(configuration, false, selection.reasonCode(), selection.detail(), null, null);
        }
        try {
            var probe = selection.provider().probe(toProviderConfiguration(configuration));
            return status(configuration, probe.available(), probe.reasonCode(), probe.detail(),
                    probe.chatModel(), probe.embeddingModel());
        } catch (RuntimeException ex) {
            return status(configuration, false, "PROVIDER_UNREACHABLE", "provider is unavailable", null, null);
        }
    }

    private AiModelProvider requireProvider(AiSettingsService.ProviderConfiguration configuration) {
        var selection = selectProvider(configuration);
        if (selection.provider() == null) {
            throw new AiProviderUnavailableException(selection.detail());
        }
        return selection.provider();
    }

    private <T> T invoke(Supplier<T> call, AiProviderConfiguration configuration, int estimatedTokens) {
        var permit = requestGuard.acquire(configuration, estimatedTokens);
        var started = new AtomicBoolean();
        CompletableFuture<T> task = CompletableFuture.supplyAsync(() -> {
            started.set(true);
            try {
                return call.get();
            } finally {
                permit.close();
            }
        }, providerExecutor);
        try {
            return task.get(configuration.requestTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            task.cancel(true);
            if (!started.get()) {
                permit.close();
            }
            throw new AiProviderUnavailableException("provider call timed out", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiProviderUnavailableException("provider call interrupted", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw new AiProviderUnavailableException("provider call failed", runtimeException);
            }
            throw new AiProviderUnavailableException("provider call failed", cause);
        }
    }

    private ProviderSelection selectProvider(AiSettingsService.ProviderConfiguration configuration) {
        if (!configuration.enabled()) {
            return new ProviderSelection(null, "AI_DISABLED", "AI is disabled");
        }
        AiModelProvider provider = findProvider(configuration.providerId());
        if (provider == null) {
            return new ProviderSelection(null, "PROVIDER_NOT_FOUND", "configured provider is not installed");
        }
        if (provider.requiresOutboundAccess() && !configuration.outboundEnabled()) {
            return new ProviderSelection(null, "OUTBOUND_DISABLED", "outbound model access is disabled");
        }
        if (provider.requiresCredential()
                && (configuration.credential() == null || configuration.credential().isBlank())) {
            return new ProviderSelection(null, "CREDENTIAL_MISSING", "provider credentials are not configured");
        }
        return new ProviderSelection(provider, "AVAILABLE", "provider is ready");
    }

    private record ProviderSelection(AiModelProvider provider, String reasonCode, String detail) {
    }

    private AiModelProvider findProvider(String id) {
        return providers.stream().filter(provider -> provider.id().equals(id)).findFirst().orElse(null);
    }

    private AiProviderConfiguration toProviderConfiguration(AiSettingsService.ProviderConfiguration configuration) {
        return new AiProviderConfiguration(
                configuration.providerId(), configuration.credential(), configuration.outboundEnabled(),
                configuration.requestsPerMinute(), configuration.maxContextTokens(),
                configuration.maxConcurrentRequests(), configuration.requestTimeoutSeconds());
    }

    private AiStatus status(
            AiSettingsService.ProviderConfiguration configuration,
            boolean available,
            String reasonCode,
            String detail,
            String chatModel,
            String embeddingModel
    ) {
        return new AiStatus(available, reasonCode, detail, configuration.providerId(), chatModel,
                embeddingModel, configuration.outboundEnabled(), configuration.requestsPerMinute(),
                configuration.maxContextTokens(), configuration.maxConcurrentRequests(),
                configuration.requestTimeoutSeconds());
    }
}
