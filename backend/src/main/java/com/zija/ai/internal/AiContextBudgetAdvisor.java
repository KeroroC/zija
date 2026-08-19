package com.zija.ai.internal;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

/** Checks the prompt generated for every iteration of Spring AI's tool loop. */
final class AiContextBudgetAdvisor implements CallAdvisor {

    private final int maxContextTokens;

    AiContextBudgetAdvisor(int maxContextTokens) {
        this.maxContextTokens = maxContextTokens;
    }

    @Override
    public ChatClientResponse adviseCall(
            ChatClientRequest request,
            CallAdvisorChain chain
    ) {
        checkContext(request.prompt(), maxContextTokens);
        return chain.nextCall(request);
    }

    @Override
    public String getName() {
        return "zija-ai-context-budget";
    }

    /**
     * Spring AI's tool advisor rebuilds a prompt after every tool call. Include tool
     * response payloads and tool definitions in the estimate so a later iteration cannot
     * bypass the deployment context limit.
     */
    static void checkContext(Prompt prompt, int maxContextTokens) {
        if (maxContextTokens < 1 || estimatePromptTokens(prompt) > maxContextTokens) {
            throw new AiRequestLimitException("AI_CONTEXT_LIMIT_EXCEEDED", "AI context limit exceeded");
        }
    }

    static int estimatePromptTokens(Prompt prompt) {
        int estimate = prompt.getInstructions().stream()
                .mapToInt(AiContextBudgetAdvisor::estimateMessageTokens)
                .sum();
        if (prompt.getOptions() instanceof ToolCallingChatOptions options
                && options.getToolCallbacks() != null) {
            estimate += options.getToolCallbacks().stream()
                    .mapToInt(AiContextBudgetAdvisor::estimateToolTokens)
                    .sum();
        }
        return Math.max(1, estimate);
    }

    private static int estimateMessageTokens(Message message) {
        int estimate = AiRequestGuard.estimateTokens(message.getText() == null ? "" : message.getText());
        if (message instanceof ToolResponseMessage toolResponse) {
            estimate += toolResponse.getResponses().stream()
                    .mapToInt(response -> AiRequestGuard.estimateTokens(
                            String.valueOf(response.id()) + response.name() + response.responseData()))
                    .sum();
        } else if (message instanceof AssistantMessage assistantMessage) {
            estimate += assistantMessage.getToolCalls().stream()
                    .mapToInt(call -> AiRequestGuard.estimateTokens(
                            String.valueOf(call.name()) + String.valueOf(call.arguments())))
                    .sum();
        }
        return estimate;
    }

    private static int estimateToolTokens(ToolCallback callback) {
        var definition = callback.getToolDefinition();
        return AiRequestGuard.estimateTokens(
                definition.name() + definition.description() + definition.inputSchema());
    }

    @Override
    public int getOrder() {
        return ToolCallingAdvisor.DEFAULT_ORDER + 1;
    }
}
