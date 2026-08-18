package com.zija.ai.internal;

import com.zija.ai.AiApi;

/** Module-owned provider seam; Spring AI and provider SDK types never cross it. */
interface AiModelProvider {

    String id();

    boolean requiresOutboundAccess();

    boolean requiresCredential();

    ProbeResult probe(AiProviderConfiguration configuration);

    AiApi.ChatReply complete(AiApi.ChatRequest request, AiProviderConfiguration configuration);

    AiApi.EmbeddingReply embed(AiApi.EmbeddingRequest request, AiProviderConfiguration configuration);

    record ProbeResult(boolean available, String reasonCode, String detail,
                       String chatModel, String embeddingModel) {

        static ProbeResult available(String chatModel, String embeddingModel) {
            return new ProbeResult(true, "AVAILABLE", "provider is ready", chatModel, embeddingModel);
        }

        static ProbeResult unavailable(String reasonCode, String detail,
                                       String chatModel, String embeddingModel) {
            return new ProbeResult(false, reasonCode, detail, chatModel, embeddingModel);
        }
    }
}
