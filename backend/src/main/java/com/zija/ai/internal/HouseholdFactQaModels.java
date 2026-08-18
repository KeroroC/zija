package com.zija.ai.internal;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 家庭事实问答的入参与答案模型。
 *
 * <p>答案由三层组成：自然语言摘要（模型生成）、结构化结果（工具返回的确定性事实）与
 * 回答依据（来源类别 + 数据时间）。跳转指向权威业务页面，由前端按类型 + id 组路由。</p>
 */
final class HouseholdFactQaModels {

    private HouseholdFactQaModels() {
    }

    record QaRequest(String question) {
    }

    record Answer(
            String question,
            boolean modelAvailable,
            String reasonCode,
            String summary,
            List<StructuredResult> structuredResults,
            List<AnswerSource> sources,
            List<Jump> jumps,
            OffsetDateTime dataTime
    ) {
    }

    /** 一组确定性结构化结果（表格行，行为列名 → 展示值）。 */
    record StructuredResult(String kind, String title, List<Map<String, String>> rows) {
    }

    /** 回答来源与数据时间。category 用稳定常量（HOUSEHOLD_FACT）。 */
    record AnswerSource(String category, String label, OffsetDateTime dataTime, boolean available, String note) {
    }

    /** 权威页面跳转。type ∈ ITEM / LOT / LOCATION / MOVEMENT / REMINDER。 */
    record Jump(String type, String label, String itemId, String lotId, String locationId) {
    }

    /** 收集工具执行期间产生的结构化结果与跳转（受控、确定性）。 */
    static final class Collector {
        private final List<StructuredResult> results = new ArrayList<>();
        private final List<Jump> jumps = new ArrayList<>();
        private boolean factSourceUnavailable;

        void addResult(StructuredResult result) {
            results.add(result);
        }

        void addJump(Jump jump) {
            jumps.add(jump);
        }

        /** 标记至少一个家庭事实来源在当前查询时不可用（工具返回 UNAVAILABLE）。 */
        void markFactSourceUnavailable() {
            factSourceUnavailable = true;
        }

        boolean factSourceUnavailable() {
            return factSourceUnavailable;
        }

        List<StructuredResult> results() {
            return List.copyOf(results);
        }

        List<Jump> jumps() {
            return List.copyOf(jumps);
        }
    }
}
