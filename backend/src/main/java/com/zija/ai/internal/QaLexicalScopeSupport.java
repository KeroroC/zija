package com.zija.ai.internal;

import com.zija.ai.internal.HouseholdFactQaModels.QaTarget;

import java.util.List;
import java.util.Locale;

/** 问答范围词表推荐（无模型调用）。 */
final class QaLexicalScopeSupport {

    static final String HOUSEHOLD_FACT = QaScopePlanner.HOUSEHOLD_FACT;
    static final String KNOWLEDGE_SOURCE = QaScopePlanner.KNOWLEDGE_SOURCE;
    static final String BOTH = QaScopePlanner.BOTH;

    private static final List<String> FACT_TERMS = List.of(
            "库存", "还有", "多少", "哪里", "在哪", "位置", "批次", "到期", "临期",
            "低库存", "缺货", "流水", "入库", "领用", "报损", "提醒", "当前", "过期");
    private static final List<String> KNOWLEDGE_TERMS = List.of(
            "怎么", "如何", "清洁", "维护", "保养", "使用", "说明", "故障", "注意", "步骤", "资料");

    private QaLexicalScopeSupport() {
    }

    static String recommend(String question, QaTarget pageTarget) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        boolean fact = containsAny(normalized, FACT_TERMS);
        boolean knowledge = containsAny(normalized, KNOWLEDGE_TERMS);
        if (fact && knowledge) {
            return BOTH;
        }
        if (knowledge) {
            return KNOWLEDGE_SOURCE;
        }
        if (fact) {
            return HOUSEHOLD_FACT;
        }
        if (pageTarget != null && isKnowledgeTarget(pageTarget.type())) {
            return BOTH;
        }
        return HOUSEHOLD_FACT;
    }

    private static boolean containsAny(String value, List<String> terms) {
        return terms.stream().anyMatch(value::contains);
    }

    private static boolean isKnowledgeTarget(String type) {
        return "ITEM".equals(type) || "LOT".equals(type);
    }
}
