package com.zija.ai;

import java.util.List;

/**
 * Public boundary for optional AI capabilities.
 *
 * <p>The API deliberately contains no Spring AI types. Provider-specific model clients,
 * tool callbacks and vector-store details stay inside the AI module so another provider
 * can replace the current local-first implementation without changing business modules.
 */
public interface AiApi {

    /** Configured provider identifier, such as {@code ollama}. */
    String providerId();

    /** Complete a prompt through the configured provider. */
    ChatReply complete(ChatRequest request);

    /** Create embeddings for a batch of texts through the configured provider. */
    EmbeddingReply embed(EmbeddingRequest request);

    record ChatRequest(String prompt) {
        public ChatRequest {
            if (prompt == null || prompt.isBlank()) {
                throw new IllegalArgumentException("prompt must not be blank");
            }
        }
    }

    record ChatReply(String content) {
    }

    record EmbeddingRequest(List<String> inputs) {
        public EmbeddingRequest {
            if (inputs == null || inputs.isEmpty()
                    || inputs.stream().anyMatch(input -> input == null || input.isBlank())) {
                throw new IllegalArgumentException("inputs must contain non-blank values");
            }
            inputs = List.copyOf(inputs);
        }
    }

    record EmbeddingReply(List<float[]> vectors) {
        public EmbeddingReply {
            vectors = List.copyOf(vectors);
        }
    }
}
