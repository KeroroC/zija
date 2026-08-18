package com.zija.ai.internal;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 家庭事实问答的入参与答案模型。
 *
 * <p>答案由三层组成：自然语言摘要（模型生成）、结构化结果（工具返回的确定性事实）与
 * 回答依据（来源类别 + 数据时间）。跳转指向权威业务页面，由前端按类型 + id 组路由。</p>
 */
final class HouseholdFactQaModels {

    private HouseholdFactQaModels() {
    }

    record QaRequest(String question, KnowledgeScope scope) {

        QaRequest(String question) {
            this(question, null);
        }
    }

    record KnowledgeScope(String type, UUID id) {
        KnowledgeScope {
            if (type == null || type.isBlank() || id == null) {
                throw new IllegalArgumentException("知识问答范围类型和对象不能为空");
            }
            type = type.trim().toUpperCase(java.util.Locale.ROOT);
            if (!"ITEM".equals(type) && !"LOT".equals(type)) {
                throw new IllegalArgumentException("知识问答范围仅支持 ITEM 或 LOT");
            }
        }
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

    /** 回答来源。知识来源的附件、挂载点与文本定位均来自检索结果，不由模型生成。 */
    record AnswerSource(
            String category,
            String label,
            OffsetDateTime dataTime,
            boolean available,
            String note,
            UUID attachmentId,
            String attachmentName,
            String attachmentUrl,
            String mountType,
            UUID mountId,
            String mountLabel,
            Integer pageNumber,
            String sectionPath,
            String excerpt,
            Integer charStart,
            Integer charEnd
    ) {

        AnswerSource(String category, String label, OffsetDateTime dataTime, boolean available, String note) {
            this(category, label, dataTime, available, note,
                    null, null, null, null, null, null, null, null, null, null, null);
        }
    }

    /** 权威页面跳转。type ∈ ITEM / LOT / LOCATION / MOVEMENT / REMINDER / ATTACHMENT。 */
    record Jump(String type, String label, String itemId, String lotId, String locationId, String attachmentId) {

        Jump(String type, String label, String itemId, String lotId, String locationId) {
            this(type, label, itemId, lotId, locationId, null);
        }
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
