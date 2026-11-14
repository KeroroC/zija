package com.zija.ai.internal;

import com.zija.ai.internal.HouseholdFactQaModels.QaTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
/** 在挂工具给 ChatClient 前，用 Jev 收窄 {@link HouseholdFactTools} 子集。 */
@Component
class JevFactToolPrerouter {

    private static final Logger log = LoggerFactory.getLogger(JevFactToolPrerouter.class);

    private final JevSettings settings;
    private final JevDecisionClient decisionClient;

    JevFactToolPrerouter(JevSettings settings, JevDecisionClient decisionClient) {
        this.settings = settings;
        this.decisionClient = decisionClient;
    }

    /**
     * @return 全量工具实例，或仅含子集的 {@link ToolCallback} 数组（供 {@link SpringAiProvider} 注册）
     */
    Object[] toolsForModel(
            HouseholdFactTools tools,
            String question,
            QaTarget target
    ) {
        HouseholdFactToolIntent lexical = HouseholdFactIntentLexicon.detect(question, target);
        if (!settings.configured()) {
            return new Object[] {tools};
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("question", question == null ? "" : question);
        if (target != null) {
            state.put("targetType", target.type());
            state.put("targetLabel", target.label());
        }
        state.put("lexicalIntent", lexical.name());
        Map<String, String> criteria = new LinkedHashMap<>();
        for (HouseholdFactToolIntent intent : HouseholdFactToolIntent.jevCriteriaKeys()) {
            criteria.put(intent.name(), intentDescription(intent));
        }
        try {
            JevChoiceAnswer answer = decisionClient.decideChoice(
                    state,
                    "factToolIntent",
                    "选择回答该家庭事实问题最可能需要的只读工具意图标签。",
                    criteria);
            HouseholdFactToolIntent chosen = HouseholdFactToolIntent.fromJevChoice(answer.choice());
            if (answer.confidence() < settings.minConfidence() || chosen == HouseholdFactToolIntent.GENERAL) {
                log.info("Jev 工具预路由置信度不足或 GENERAL，挂全量工具: choice={} confidence={} lexical={}",
                        answer.choice(), answer.confidence(), lexical);
                return new Object[] {tools};
            }
            Set<String> allowed = chosen.toolNames();
            ToolCallback[] callbacks = filterCallbacks(tools, allowed);
            if (callbacks.length == 0) {
                log.warn("Jev 工具预路由过滤后为空，挂全量工具: choice={}", answer.choice());
                return new Object[] {tools};
            }
            log.info("Jev 工具预路由: choice={} confidence={} tools={} lexical={}",
                    answer.choice(), answer.confidence(), allowed, lexical);
            return new Object[] {callbacks};
        } catch (RuntimeException exception) {
            log.warn("Jev 工具预路由失败，挂全量工具: reason={} lexical={}",
                    exception.getMessage(), lexical);
            return new Object[] {tools};
        }
    }

    private static String intentDescription(HouseholdFactToolIntent intent) {
        return switch (intent) {
            case ITEM_STOCK -> "查某物品库存、快照、数量分布。";
            case LOCATION_STOCK -> "查某位置及子位置内的库存。";
            case MOVEMENTS -> "查流水、入库、领用、报损等变动记录。";
            case EXPIRY -> "查临期或已过期批次。";
            case LOW_STOCK -> "查低库存/缺货物品列表。";
            case REMINDERS -> "查待处理提醒任务（非规则配置说明）。";
            case GENERAL -> "意图不清，需要全量工具。";
        };
    }

    private static ToolCallback[] filterCallbacks(HouseholdFactTools tools, Set<String> allowedNames) {
        ToolCallback[] all = MethodToolCallbackProvider.builder().toolObjects(tools).build().getToolCallbacks();
        return Arrays.stream(all)
                .filter(callback -> allowedNames.contains(callback.getToolDefinition().name()))
                .toArray(ToolCallback[]::new);
    }
}
