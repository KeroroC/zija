package com.zija.ai.internal;

import com.zija.ai.AiApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Spring AI adapter for the configured provider.
 *
 * <p>The adapter reports availability by asking Ollama for its local model list. A failed
 * probe is converted into a status result; it never prevents the application from starting.
 */
@Service
class SpringAiProvider implements AiModelProvider, AiQaModelProvider {

    private final ChatClient.Builder chatClientBuilder;
    private final EmbeddingModel embeddingModel;
    private final OllamaApi ollamaApi;
    private final AiModelNames modelNames;

    SpringAiProvider(
            ChatClient.Builder chatClientBuilder,
            EmbeddingModel embeddingModel,
            OllamaApi ollamaApi,
            AiModelNames modelNames
    ) {
        this.chatClientBuilder = chatClientBuilder;
        this.embeddingModel = embeddingModel;
        this.ollamaApi = ollamaApi;
        this.modelNames = modelNames;
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
            // OllamaApi.listModels() is non-null by contract (Objects.requireNonNull); the
            // ListModelResponse record component is not @Nullable. An empty list means the
            // provider is reachable but has no matching models — the chat/embedding checks
            // below report the appropriate "MODEL_MISSING" status.
            var models = ollamaApi.listModels();
            boolean chatAvailable = models.models().stream()
                    .anyMatch(model -> modelMatches(model, modelNames.chatModel()));
            boolean embeddingAvailable = models.models().stream()
                    .anyMatch(model -> modelMatches(model, modelNames.embeddingModel()));
            if (!chatAvailable) {
                return ProbeResult.unavailable("CHAT_MODEL_MISSING", "configured chat model is unavailable",
                        modelNames.chatModel(), modelNames.embeddingModel());
            }
            if (!embeddingAvailable) {
                return ProbeResult.unavailable("EMBEDDING_MODEL_MISSING", "configured embedding model is unavailable",
                        modelNames.chatModel(), modelNames.embeddingModel());
            }
            if (embeddingModel.dimensions() != AiService.EMBEDDING_DIMENSIONS) {
                return ProbeResult.unavailable("EMBEDDING_DIMENSION_MISMATCH",
                        "embedding model must produce 1024 dimensions",
                        modelNames.chatModel(), modelNames.embeddingModel());
            }
            return ProbeResult.available(modelNames.chatModel(), modelNames.embeddingModel());
        } catch (RuntimeException ex) {
            return ProbeResult.unavailable("PROVIDER_UNREACHABLE", "provider is unavailable",
                    modelNames.chatModel(), modelNames.embeddingModel());
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
    public String completeQa(
            String systemPrompt,
            String userPrompt,
            Object[] tools,
            AiProviderConfiguration configuration
    ) {
        var prompt = chatClientBuilder.build()
                .prompt()
                .system(systemPrompt)
                .user(userPrompt);
        if (tools.length > 0) {
            prompt.tools(tools)
                    .advisors(new AiContextBudgetAdvisor(configuration.maxContextTokens()));
        }
        String content = prompt.call().content();
        return content == null ? "" : content;
    }

    @Override
    public AiApi.EmbeddingReply embed(AiApi.EmbeddingRequest request, AiProviderConfiguration configuration) {
        return new AiApi.EmbeddingReply(embeddingModel.embed(request.inputs()));
    }
}
