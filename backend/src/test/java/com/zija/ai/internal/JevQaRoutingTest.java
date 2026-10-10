package com.zija.ai.internal;

import com.zija.ai.internal.HouseholdFactQaModels.QaTarget;
import com.zija.inventory.InventoryApi;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class JevQaRoutingTest {

    private static final UUID HOUSEHOLD_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void disabledJevScopeMatchesLexicalOnly() {
        JevQaScopeRecommender recommender = scopeRecommender(false, mockScopeClient("KNOWLEDGE_SOURCE", 0.99));
        assertThat(recommender.recommend("牛奶还有多少？", null))
                .isEqualTo(QaScopePlanner.HOUSEHOLD_FACT);
    }

    @Test
    void enabledJevScopeUsesHighConfidenceChoice() {
        JevQaScopeRecommender recommender = scopeRecommender(true, mockScopeClient("KNOWLEDGE_SOURCE", 0.9));
        assertThat(recommender.recommend("牛奶还有多少？", null))
                .isEqualTo(QaScopePlanner.KNOWLEDGE_SOURCE);
    }

    @Test
    void enabledJevScopeFallsBackOnLowConfidence() {
        JevQaScopeRecommender recommender = scopeRecommender(true, mockScopeClient("KNOWLEDGE_SOURCE", 0.2));
        assertThat(recommender.recommend("滤网怎么清洁？", null))
                .isEqualTo(QaScopePlanner.KNOWLEDGE_SOURCE);
    }

    @Test
    void disabledJevToolPrerouterReturnsFullToolsObject() {
        var prerouter = toolPrerouter(false, mockToolClient("LOW_STOCK", 0.99));
        var tools = stubTools();
        Object[] registration = prerouter.toolsForModel(tools, "低库存有哪些？", null);
        assertThat(registration).hasSize(1);
        assertThat(registration[0]).isSameAs(tools);
    }

    @Test
    void enabledJevToolPrerouterNarrowsCallbacks() {
        var prerouter = toolPrerouter(true, mockToolClient("LOW_STOCK", 0.9));
        var tools = stubTools();
        Object[] registration = prerouter.toolsForModel(tools, "低库存有哪些？", null);
        assertThat(registration).hasSize(1);
        assertThat(registration[0]).isInstanceOf(ToolCallback[].class);
        ToolCallback[] callbacks = (ToolCallback[]) registration[0];
        Set<String> names = Set.of(callbacks).stream()
                .map(c -> c.getToolDefinition().name())
                .collect(Collectors.toSet());
        assertThat(names).containsExactlyInAnyOrder("lowStock", "searchItems");
    }

    @Test
    void enabledJevToolPrerouterFallsBackToFullToolsOnLowConfidence() {
        var prerouter = toolPrerouter(true, mockToolClient("LOW_STOCK", 0.1));
        var tools = stubTools();
        Object[] registration = prerouter.toolsForModel(tools, "低库存有哪些？", null);
        assertThat(registration[0]).isSameAs(tools);
    }

    @Test
    void intentLexiconAlignsWithFallbackKeywords() {
        assertThat(HouseholdFactIntentLexicon.detect("低库存有哪些？", null))
                .isEqualTo(HouseholdFactToolIntent.LOW_STOCK);
        QaTarget location = new QaTarget("LOCATION", UUID.randomUUID(), "厨房");
        assertThat(HouseholdFactIntentLexicon.detect("这里有什么？", location))
                .isEqualTo(HouseholdFactToolIntent.LOCATION_STOCK);
    }

    private static JevQaScopeRecommender scopeRecommender(boolean enabled, JevDecisionClient client) {
        JevSettings settings = new JevSettings(
                enabled, enabled ? "test-key" : "", "http://localhost", "typesafe/jev-1.13", 5, 0.55);
        return new JevQaScopeRecommender(settings, client);
    }

    private static JevFactToolPrerouter toolPrerouter(boolean enabled, JevDecisionClient client) {
        JevSettings settings = new JevSettings(
                enabled, enabled ? "test-key" : "", "http://localhost", "typesafe/jev-1.13", 5, 0.55);
        return new JevFactToolPrerouter(settings, client);
    }

    private static JevDecisionClient mockScopeClient(String choice, double confidence) {
        return (state, questionId, instructions, criteria) -> {
            assertThat(questionId).isEqualTo("answerScope");
            return new JevChoiceAnswer(choice, confidence, Map.of(choice, confidence));
        };
    }

    private static JevDecisionClient mockToolClient(String choice, double confidence) {
        return (state, questionId, instructions, criteria) -> {
            assertThat(questionId).isEqualTo("factToolIntent");
            return new JevChoiceAnswer(choice, confidence, Map.of(choice, confidence));
        };
    }

    private static HouseholdFactTools stubTools() {
        return new HouseholdFactTools(
                HOUSEHOLD_ID,
                Mockito.mock(HouseholdFactQueries.class),
                new HouseholdFactQaModels.Collector(),
                null,
                Mockito.mock(InventoryApi.class));
    }
}
