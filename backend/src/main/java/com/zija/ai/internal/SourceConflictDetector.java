package com.zija.ai.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** 从两类回答的同语义字段中提取可解释冲突。 */
final class SourceConflictDetector {

    private static final List<ConflictRule> RULES = List.of(
            new ConflictRule("QUANTITY", Pattern.compile(
                    "(?:当前总库存|当前库存|剩余数量|库存数量|库存)"
                            + "\\s*(?:为|是|有|剩余|[:：])?\\s*([0-9]+(?:\\.[0-9]+)?)"),
                    Pattern.compile("数量\\s*(?:为|是|有|剩余|[:：])?\\s*([0-9]+(?:\\.[0-9]+)?)")),
            new ConflictRule("DATE", Pattern.compile(
                    "(?:到期日期|到期日|有效期至|有效期到|保质期至|保质期到)"
                            + "\\s*(?:为|是|[:：])?\\s*(20[0-9]{2}-[01][0-9]-[0-3][0-9])"), null),
            new ConflictRule("LOCATION", Pattern.compile(
                    "(?:存放位置|所在位置|库存位置|位置|存放在|放在|位于)"
                            + "\\s*(?:为|是|在|[:：])?\\s*"
                            + "([^\\s，。；;、,]+(?:\\s*/\\s*[^\\s，。；;、,]+)*)"), null),
            new ConflictRule("STATUS", Pattern.compile(
                    "(?:当前状态|库存状态|状态)\\s*(?:为|是|[:：])?\\s*([^\\s，。；;、,]+)"), null),
            new ConflictRule("UNIT", Pattern.compile(
                    "(?:计量单位|库存单位|单位)\\s*(?:为|是|[:：])?\\s*([^\\s，。；;、,]+)"), null));

    private SourceConflictDetector() {
    }

    static List<HouseholdFactQaModels.SourceConflict> detect(
            HouseholdFactQaModels.Answer fact,
            HouseholdFactQaModels.Answer knowledge
    ) {
        List<HouseholdFactQaModels.SourceConflict> conflicts = new ArrayList<>();
        for (ConflictRule rule : RULES) {
            String factValue = factValue(rule, fact);
            String knowledgeValue = knowledgeValue(rule, knowledge);
            if (factValue != null && knowledgeValue != null && !factValue.equals(knowledgeValue)) {
                conflicts.add(new HouseholdFactQaModels.SourceConflict(
                        rule.kind(), factValue, knowledgeValue,
                        "家庭事实与知识来源记录不一致；家庭事实代表当前状态，附件内容按原文保留"));
            }
        }
        return List.copyOf(conflicts);
    }

    private static String factValue(ConflictRule rule, HouseholdFactQaModels.Answer answer) {
        ValueResolution structured = resolve(rule, structuredEvidence(answer));
        if (structured.value() != null) return structured.value();
        if ("QUANTITY".equals(rule.kind())) {
            String aggregate = itemStockTotal(answer);
            if (aggregate != null) return aggregate;
        }
        if (structured.matched()) return null;
        return resolve(rule, answer.summary()).value();
    }

    private static String knowledgeValue(ConflictRule rule, HouseholdFactQaModels.Answer answer) {
        ValueResolution excerpts = resolve(rule, excerptEvidence(answer));
        if (excerpts.matched()) return excerpts.value();
        ValueResolution structured = resolve(rule, structuredEvidence(answer));
        if (structured.matched()) return structured.value();
        return resolve(rule, answer.summary()).value();
    }

    private static String structuredEvidence(HouseholdFactQaModels.Answer answer) {
        StringBuilder evidence = new StringBuilder();
        for (var result : answer.structuredResults()) {
            for (var row : result.rows()) {
                row.forEach((key, value) -> evidence.append(key).append(' ').append(value).append('\n'));
            }
        }
        return evidence.toString();
    }

    private static String excerptEvidence(HouseholdFactQaModels.Answer answer) {
        StringBuilder evidence = new StringBuilder();
        for (var source : answer.sources()) {
            if (source.excerpt() != null) evidence.append(source.excerpt()).append('\n');
        }
        return evidence.toString();
    }

    private static String itemStockTotal(HouseholdFactQaModels.Answer answer) {
        List<HouseholdFactQaModels.StructuredResult> stockResults = answer.structuredResults().stream()
                .filter(result -> "ITEM_STOCK".equals(result.kind()))
                .toList();
        if (stockResults.size() != 1 || stockResults.getFirst().rows().isEmpty()) return null;

        BigDecimal total = BigDecimal.ZERO;
        for (var row : stockResults.getFirst().rows()) {
            String quantity = row.get("数量");
            if (quantity == null) return null;
            try {
                total = total.add(new BigDecimal(quantity));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return total.stripTrailingZeros().toPlainString();
    }

    private static ValueResolution resolve(ConflictRule rule, String evidence) {
        ValueResolution primary = uniqueValue(rule.kind(), rule.pattern(), evidence);
        if (primary.matched() || rule.fallbackPattern() == null) return primary;
        return uniqueValue(rule.kind(), rule.fallbackPattern(), evidence);
    }

    private static ValueResolution uniqueValue(String kind, Pattern pattern, String evidence) {
        var matcher = pattern.matcher(evidence == null ? "" : evidence);
        var values = new LinkedHashSet<String>();
        while (matcher.find()) {
            values.add(normalize(kind, matcher.group(1)));
        }
        return new ValueResolution(!values.isEmpty(), values.size() == 1 ? values.getFirst() : null);
    }

    private static String normalize(String kind, String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("QUANTITY".equals(kind)) {
            return new BigDecimal(normalized).stripTrailingZeros().toPlainString();
        }
        return normalized.replaceAll("\\s*/\\s*", " / ");
    }

    private record ConflictRule(String kind, Pattern pattern, Pattern fallbackPattern) {
    }

    private record ValueResolution(boolean matched, String value) {
    }
}
