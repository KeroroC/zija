package com.zija.ai.internal;

import com.zija.ai.internal.HouseholdFactQaModels.QaTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** AUTO 范围下用 Jev choice 增强词表推荐；失败或低置信时回退词表。 */
@Component
class JevQaScopeRecommender {

    private static final Logger log = LoggerFactory.getLogger(JevQaScopeRecommender.class);

    private final JevSettings settings;
    private final JevDecisionClient decisionClient;

    JevQaScopeRecommender(JevSettings settings, JevDecisionClient decisionClient) {
        this.settings = settings;
        this.decisionClient = decisionClient;
    }

    String recommend(String question, QaTarget pageTarget) {
        String lexical = QaLexicalScopeSupport.recommend(question, pageTarget);
        if (!settings.configured()) {
            return lexical;
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("question", question == null ? "" : question);
        if (pageTarget != null) {
            state.put("pageContextType", pageTarget.type());
            state.put("pageContextLabel", pageTarget.label());
        }
        Map<String, String> criteria = Map.of(
                QaScopePlanner.HOUSEHOLD_FACT,
                "用户要查库存、位置、批次、流水、临期、低库存、提醒任务等家庭事实数据。",
                QaScopePlanner.KNOWLEDGE_SOURCE,
                "用户要使用方法、维护、清洁、故障处理等挂载在物品/批次上的知识资料。",
                QaScopePlanner.BOTH,
                "问题同时需要家庭事实与知识资料，或意图不清但页面上下文是具体物品/批次。");
        try {
            JevChoiceAnswer answer = decisionClient.decideChoice(
                    state,
                    "answerScope",
                    "根据用户问题与页面上下文，选择最合适的只读问答范围。",
                    criteria);
            if (answer.confidence() < settings.minConfidence()) {
                log.info("Jev 范围决策置信度不足，回退词表: choice={} confidence={} lexical={}",
                        answer.choice(), answer.confidence(), lexical);
                return lexical;
            }
            log.info("Jev 范围决策: choice={} confidence={} fallbackLexical={}",
                    answer.choice(), answer.confidence(), lexical);
            return answer.choice();
        } catch (RuntimeException exception) {
            log.warn("Jev 范围决策失败，回退词表: reason={} lexical={}",
                    exception.getMessage(), lexical);
            return lexical;
        }
    }
}
