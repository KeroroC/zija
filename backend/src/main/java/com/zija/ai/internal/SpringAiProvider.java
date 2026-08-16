package com.zija.ai.internal;

import com.zija.ai.AiApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Spring AI adapter for the configured provider.
 *
 * <p>The adapter reports availability by asking Ollama for its local model list. A failed
 * probe is converted into a status result; it never prevents the application from starting.
 */
@Service
class SpringAiProvider implements AiModelProvider {

    private final ChatClient.Builder chatClientBuilder;
    private final EmbeddingModel embeddingModel;
    private final OllamaApi ollamaApi;
    private final String chatModel;
    private final String embeddingModelName;

    SpringAiProvider(
            ChatClient.Builder chatClientBuilder,
            EmbeddingModel embeddingModel,
            OllamaApi ollamaApi,
            @Value("${spring.ai.ollama.chat.model:qwen2.5:7b}") String chatModel,
            @Value("${spring.ai.ollama.embedding.model:nomic-embed-text}") String embeddingModelName
    ) {
        this.chatClientBuilder = chatClientBuilder;
        this.embeddingModel = embeddingModel;
        this.ollamaApi = ollamaApi;
        this.chatModel = chatModel;
        this.embeddingModelName = embeddingModelName;
    }

    @Override
    public String id() {
        return "ollama";
    }

    @Override
    public boolean requiresOutboundAccess() {
        return false;
    }

    @Override
    public boolean requiresCredential() {
        return false;
    }

    @Override
    public ProbeResult probe(AiProviderConfiguration configuration) {
        try {
            var models = ollamaApi.listModels();
            if (models == null || models.models() == null) {
                return ProbeResult.unavailable("PROVIDER_UNREACHABLE", "provider returned no model list",
                        chatModel, embeddingModelName);
            }
            boolean chatAvailable = models.models().stream().anyMatch(model -> modelMatches(model, chatModel));
            boolean embeddingAvailable = models.models().stream()
                    .anyMatch(model -> modelMatches(model, embeddingModelName));
            if (!chatAvailable) {
                return ProbeResult.unavailable("CHAT_MODEL_MISSING", "configured chat model is unavailable",
                        chatModel, embeddingModelName);
            }
            if (!embeddingAvailable) {
                return ProbeResult.unavailable("EMBEDDING_MODEL_MISSING", "configured embedding model is unavailable",
                        chatModel, embeddingModelName);
            }
            if (embeddingModel.dimensions() != AiService.EMBEDDING_DIMENSIONS) {
                return ProbeResult.unavailable("EMBEDDING_DIMENSION_MISMATCH",
                        "embedding model must produce 768 dimensions", chatModel, embeddingModelName);
            }
            return ProbeResult.available(chatModel, embeddingModelName);
        } catch (RuntimeException ex) {
            return ProbeResult.unavailable("PROVIDER_UNREACHABLE", "provider is unavailable",
                    chatModel, embeddingModelName);
        }
    }

    private boolean modelMatches(OllamaApi.Model model, String expected) {
        return Objects.equals(expected, model.name()) || Objects.equals(expected, model.model());
    }

    @Override
    public AiApi.ChatReply complete(AiApi.ChatRequest request, AiProviderConfiguration configuration) {
        String content = chatClientBuilder.build()
                .prompt(request.prompt())
                .call()
                .content();
        return new AiApi.ChatReply(content == null ? "" : content);
    }

    @Override
    public AiApi.EmbeddingReply embed(AiApi.EmbeddingRequest request, AiProviderConfiguration configuration) {
        return new AiApi.EmbeddingReply(embeddingModel.embed(request.inputs()));
    }
}
