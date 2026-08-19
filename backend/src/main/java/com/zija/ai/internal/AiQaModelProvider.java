package com.zija.ai.internal;

/** Provider-neutral Q&A seam selected by the same deployment setting as other AI calls. */
interface AiQaModelProvider {

    String id();

    String completeQa(
            String systemPrompt,
            String userPrompt,
            Object[] tools,
            AiProviderConfiguration configuration
    );
}
