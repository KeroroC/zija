package com.zija.ai.internal;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SourceConflictDetectorTest {

    @Test
    void detectsTotalQuantityConflictDespiteMultipleStockPositionValues() {
        var fact = answer(
                "家庭事实显示当前总库存 5 台。",
                List.of(new HouseholdFactQaModels.StructuredResult(
                        "ITEM_STOCK",
                        "咖啡机库存分布",
                        List.of(
                                Map.of("位置", "厨房", "数量", "2"),
                                Map.of("位置", "客厅", "数量", "3")))));
        var knowledge = answer("知识来源记录当前库存 4 台。", List.of());

        assertThat(SourceConflictDetector.detect(fact, knowledge))
                .singleElement()
                .satisfies(conflict -> {
                    assertThat(conflict.kind()).isEqualTo("QUANTITY");
                    assertThat(conflict.factValue()).isEqualTo("5");
                    assertThat(conflict.knowledgeValue()).isEqualTo("4");
                });
    }

    @Test
    void authoritativeEvidenceWinsWhenTheModelSummaryMisstatesKnowledge() {
        var fact = answer(
                "家庭事实显示当前库存 5 台。",
                List.of(new HouseholdFactQaModels.StructuredResult(
                        "ITEM_SEARCH",
                        "物品搜索结果",
                        List.of(Map.of("物品", "咖啡机", "当前总库存", "5")))));
        var knowledge = new HouseholdFactQaModels.Answer(
                "咖啡机库存与说明书一致吗？",
                true,
                "ANSWERED",
                "知识来源显示当前库存 5 台。",
                List.of(),
                List.of(new HouseholdFactQaModels.AnswerSource(
                        "KNOWLEDGE_SOURCE", "知识来源", OffsetDateTime.parse("2026-08-19T12:00:00+08:00"),
                        true, null, null, null, null, null, null, null, null, null,
                        "说明书记录当前库存 4 台。", null, null)),
                List.of(),
                OffsetDateTime.parse("2026-08-19T12:00:00+08:00"));

        assertThat(SourceConflictDetector.detect(fact, knowledge))
                .singleElement()
                .satisfies(conflict -> {
                    assertThat(conflict.kind()).isEqualTo("QUANTITY");
                    assertThat(conflict.factValue()).isEqualTo("5");
                    assertThat(conflict.knowledgeValue()).isEqualTo("4");
                });
    }

    private static HouseholdFactQaModels.Answer answer(
            String summary,
            List<HouseholdFactQaModels.StructuredResult> results
    ) {
        return new HouseholdFactQaModels.Answer(
                "咖啡机库存与说明书一致吗？",
                true,
                "ANSWERED",
                summary,
                results,
                List.of(),
                List.of(),
                OffsetDateTime.parse("2026-08-19T12:00:00+08:00"));
    }
}
