package com.zija.ai.internal;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link SpringAiProvider#probe(AiProviderConfiguration)}.
 *
 * <p>Covers each branch of the probe state machine without spinning up a Spring context,
 * matching the project pattern in {@code ZijaSessionAuthenticationSupportTest}. The listModels
 * contract guarantees a non-null response (see {@code OllamaApi#listModels()} which wraps the
 * deserialized body in {@code Objects.requireNonNull}); the regression target is therefore the
 * downstream {@code stream().anyMatch(...)} branches and the exception handler.
 */
class SpringAiProviderTest {

    private static final String CHAT_MODEL = "qwen2.5:7b";
    private static final String EMBEDDING_MODEL = "qwen3-embedding:0.6b";

    private static final AiProviderConfiguration CONFIG = new AiProviderConfiguration(
            "ollama", null, false, 60, 4096, 4, 30);

    @Test
    void probeReportsAvailableWhenBothModelsAreListed() {
        SpringAiProvider provider = providerWith(
                ollamaApi(List.of(
                        ollamaModel(CHAT_MODEL, CHAT_MODEL),
                        ollamaModel(EMBEDDING_MODEL, EMBEDDING_MODEL))),
                embeddingModelOfDimensions(AiService.EMBEDDING_DIMENSIONS));

        AiModelProvider.ProbeResult result = provider.probe(CONFIG);

        assertThat(result.available()).isTrue();
        assertThat(result.reasonCode()).isEqualTo("AVAILABLE");
        assertThat(result.chatModel()).isEqualTo(CHAT_MODEL);
        assertThat(result.embeddingModel()).isEqualTo(EMBEDDING_MODEL);
    }

    @Test
    void probeReportsChatModelMissingWhenModelsListIsEmpty() {
        SpringAiProvider provider = providerWith(
                ollamaApi(List.of()),
                embeddingModelOfDimensions(AiService.EMBEDDING_DIMENSIONS));

        AiModelProvider.ProbeResult result = provider.probe(CONFIG);

        assertThat(result.available()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("CHAT_MODEL_MISSING");
        assertThat(result.detail()).contains("chat model");
    }

    @Test
    void probeReportsEmbeddingModelMissingWhenChatMatchesButEmbeddingDoesNot() {
        SpringAiProvider provider = providerWith(
                ollamaApi(List.of(ollamaModel(CHAT_MODEL, CHAT_MODEL))),
                embeddingModelOfDimensions(AiService.EMBEDDING_DIMENSIONS));

        AiModelProvider.ProbeResult result = provider.probe(CONFIG);

        assertThat(result.available()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("EMBEDDING_MODEL_MISSING");
    }

    @Test
    void probeReportsDimensionMismatchWhenEmbeddingDimensionIsWrong() {
        SpringAiProvider provider = providerWith(
                ollamaApi(List.of(
                        ollamaModel(CHAT_MODEL, CHAT_MODEL),
                        ollamaModel(EMBEDDING_MODEL, EMBEDDING_MODEL))),
                embeddingModelOfDimensions(AiService.EMBEDDING_DIMENSIONS + 1));

        AiModelProvider.ProbeResult result = provider.probe(CONFIG);

        assertThat(result.available()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("EMBEDDING_DIMENSION_MISMATCH");
    }

    @Test
    void probeReportsProviderUnreachableWhenListModelsThrows() {
        OllamaApi ollamaApi = mock(OllamaApi.class);
        when(ollamaApi.listModels()).thenThrow(new RuntimeException("connection refused"));
        SpringAiProvider provider = providerWith(ollamaApi,
                embeddingModelOfDimensions(AiService.EMBEDDING_DIMENSIONS));

        AiModelProvider.ProbeResult result = provider.probe(CONFIG);

        assertThat(result.available()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("PROVIDER_UNREACHABLE");
        assertThat(result.detail()).isEqualTo("provider is unavailable");
    }

    private static SpringAiProvider providerWith(OllamaApi ollamaApi, EmbeddingModel embeddingModel) {
        ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
        AiModelNames modelNames = new AiModelNames(CHAT_MODEL, EMBEDDING_MODEL);
        return new SpringAiProvider(chatClientBuilder, embeddingModel, ollamaApi, modelNames);
    }

    private static OllamaApi ollamaApi(List<OllamaApi.Model> models) {
        OllamaApi api = mock(OllamaApi.class);
        when(api.listModels()).thenReturn(new OllamaApi.ListModelResponse(models));
        return api;
    }

    private static OllamaApi.Model ollamaModel(String name, String model) {
        return new OllamaApi.Model(name, model, Instant.EPOCH, 0L, "", new OllamaApi.Model.Details(
                "", "", "", List.of(), "", ""));
    }

    private static EmbeddingModel embeddingModelOfDimensions(int dimensions) {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.dimensions()).thenReturn(dimensions);
        return model;
    }
}
