package com.zija.ai.internal;

import java.util.Map;

/**
 * 可替换的 Jev / Decisions API 客户端。只返回结构化决策，不生成长文。
 */
interface JevDecisionClient {

    /**
     * @param state      决策上下文（问题、页面类型等，勿含密钥或完整库存明细）
     * @param questionId 响应 {@code answers} 中的键
     * @param instructions choice 题说明
     * @param criteria     选项 id → 判定说明
     */
    JevChoiceAnswer decideChoice(
            Object state,
            String questionId,
            String instructions,
            Map<String, String> criteria
    );
}
