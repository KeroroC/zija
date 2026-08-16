package com.zija.ai.internal;

import com.zija.ai.AiApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Spring AI adapter for the configured provider.
 *
 * <p>Only this adapter knows about Spring AI. The orchestration layer can add read-only
 * tool callbacks to the ChatClient request without leaking those framework types through
 * {@link AiApi}.
 */
@Service
class SpringAiProvider implements AiApi {

    private final ChatClient.Builder chatClientBuilder;
    private final EmbeddingModel embeddingModel;
    private final String providerId;

    SpringAiProvider(
            ChatClient.Builder chatClientBuilder,
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.model.chat:ollama}") String providerId
    ) {
        this.chatClientBuilder = chatClientBuilder;
        this.embeddingModel = embeddingModel;
        this.providerId = providerId;
    }

    @Override
    public String providerId() {
        return providerId;
    }

    @Override
    public ChatReply complete(ChatRequest request) {
        String content = chatClientBuilder.build()
                .prompt(request.prompt())
                .call()
                .content();
        return new ChatReply(content == null ? "" : content);
    }

    @Override
    public EmbeddingReply embed(EmbeddingRequest request) {
        return new EmbeddingReply(embeddingModel.embed(request.inputs()));
    }
}
