package com.zija.ai.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 模型配置名的唯一来源：探活/状态报告（{@code SpringAiProvider}）与分块溯源元数据
 * （{@code KnowledgeChunkDocumentFactory}）共用，避免同一占位符多处声明导致默认值漂移。
 */
@Component
class AiModelNames {

    private final String chatModel;
    private final String embeddingModel;

    AiModelNames(
            @Value("${spring.ai.ollama.chat.model:qwen2.5:7b}") String chatModel,
            @Value("${spring.ai.ollama.embedding.model:qwen3-embedding:0.6b}") String embeddingModel
    ) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    String chatModel() {
        return chatModel;
    }

    String embeddingModel() {
        return embeddingModel;
    }
}
