package com.zija.ai.internal;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRequestGuardTest {

    @Test
    void rejectsRequestsBeyondTheConfiguredContextLimit() {
        var guard = new AiRequestGuard();

        assertThatThrownBy(() -> guard.acquire(configuration(10, 256, 2), 257))
                .isInstanceOf(AiRequestLimitException.class)
                .hasMessage("AI context limit exceeded");
    }

    @Test
    void countsNonAsciiTextConservativelyForTheContextLimit() {
        var guard = new AiRequestGuard();

        assertThatThrownBy(() -> guard.acquire(
                configuration(10, 256, 2), AiRequestGuard.estimateTokens("问".repeat(257))))
                .isInstanceOf(AiRequestLimitException.class)
                .hasMessage("AI context limit exceeded");
    }

    @Test
    void rejectsRequestsBeyondTheConfiguredRateLimit() {
        var guard = new AiRequestGuard();
        try (var ignored = guard.acquire(configuration(1, 1024, 2), 1)) {
            // Holding the permit is not required for rate limiting, but mirrors a real call.
        }

        assertThatThrownBy(() -> guard.acquire(configuration(1, 1024, 2), 1))
                .isInstanceOf(AiRequestLimitException.class)
                .hasMessage("AI request rate limit exceeded");
    }

    @Test
    void rejectsRequestsBeyondTheConfiguredConcurrencyLimit() {
        var guard = new AiRequestGuard();
        try (var ignored = guard.acquire(configuration(10, 1024, 1), 1)) {
            assertThatThrownBy(() -> guard.acquire(configuration(10, 1024, 1), 1))
                    .isInstanceOf(AiRequestLimitException.class)
                    .hasMessage("AI concurrency limit exceeded");
        }
    }

    @Test
    void limitsEachMemberWithoutBlockingAnotherMember() {
        var guard = new AiRequestGuard();
        UUID firstMember = UUID.randomUUID();
        UUID secondMember = UUID.randomUUID();
        var configuration = configuration(3, 1, 1024, 2);

        try (var ignored = guard.acquire(configuration, firstMember, 1)) {
            // The completed call still counts against the member's minute window.
        }

        assertThatThrownBy(() -> guard.acquire(configuration, firstMember, 1))
                .isInstanceOf(AiRequestLimitException.class)
                .extracting(exception -> ((AiRequestLimitException) exception).reasonCode())
                .isEqualTo("AI_MEMBER_RATE_LIMITED");
        try (var ignored = guard.acquire(configuration, secondMember, 1)) {
            // A different member has an independent bucket.
        }
    }

    @Test
    void limitsDeploymentAcrossDifferentMembers() {
        var guard = new AiRequestGuard();
        var configuration = configuration(2, 2, 1024, 2);

        try (var ignored = guard.acquire(configuration, UUID.randomUUID(), 1)) {
        }
        try (var ignored = guard.acquire(configuration, UUID.randomUUID(), 1)) {
        }

        assertThatThrownBy(() -> guard.acquire(configuration, UUID.randomUUID(), 1))
                .isInstanceOf(AiRequestLimitException.class)
                .extracting(exception -> ((AiRequestLimitException) exception).reasonCode())
                .isEqualTo("AI_DEPLOYMENT_RATE_LIMITED");
    }

    @Test
    void capsToolCallCountWithinOneQuestion() {
        var callBoundedCollector = new HouseholdFactQaModels.Collector();
        assertThat(List.of(
                callBoundedCollector.beginToolCall(),
                callBoundedCollector.beginToolCall(),
                callBoundedCollector.beginToolCall(),
                callBoundedCollector.beginToolCall())).containsOnly(true);
        assertThat(callBoundedCollector.beginToolCall()).isFalse();
        assertThat(callBoundedCollector.factSourceUnavailable()).isTrue();
    }

    @Test
    void countsToolResponsePayloadWhenCheckingTheNextPromptBudget() {
        var prompt = new Prompt(List.of(
                new SystemMessage("只根据工具回答"),
                new UserMessage("牛奶还有多少？"),
                AssistantMessage.builder()
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "call-1", "function", "searchItems", "{\"keyword\":\"牛奶\"}")))
                        .build(),
                ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse(
                                "call-1", "searchItems", "家庭事实".repeat(80))))
                        .build()));

        int promptTokens = AiContextBudgetAdvisor.estimatePromptTokens(prompt);

        assertThat(promptTokens).isGreaterThan(AiRequestGuard.estimateTokens("牛奶还有多少？"));
        assertThatThrownBy(() -> AiContextBudgetAdvisor.checkContext(prompt, promptTokens - 1))
                .isInstanceOf(AiRequestLimitException.class)
                .extracting(exception -> ((AiRequestLimitException) exception).reasonCode())
                .isEqualTo("AI_CONTEXT_LIMIT_EXCEEDED");
    }

    private AiProviderConfiguration configuration(int rate, int context, int concurrency) {
        return configuration(rate, rate, context, concurrency);
    }

    private AiProviderConfiguration configuration(
            int deploymentRate,
            int memberRate,
            int context,
            int concurrency
    ) {
        return new AiProviderConfiguration(
                "deterministic", null, false, deploymentRate, memberRate, context, concurrency, 1);
    }
}
