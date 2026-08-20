package com.zija.ai.internal;

import com.zija.ai.internal.HouseholdFactQaModels.QaTarget;
import com.zija.ai.internal.HouseholdFactQaModels.QaRequest;
import com.zija.ai.internal.HouseholdFactQaModels.ScopeCandidate;
import com.zija.ai.internal.HouseholdFactQaModels.ScopePlan;
import com.zija.catalog.CatalogApi;
import com.zija.inventory.InventoryApi;
import com.zija.location.LocationApi;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** 只用服务端权威对象和确定性规则推荐、校验并解析问答范围。 */
@Component
class QaScopePlanner {

    static final String AUTO = "AUTO";
    static final String HOUSEHOLD_FACT = "HOUSEHOLD_FACT";
    static final String KNOWLEDGE_SOURCE = "KNOWLEDGE_SOURCE";
    static final String BOTH = "BOTH";

    private static final List<String> FACT_TERMS = List.of(
            "库存", "还有", "多少", "哪里", "在哪", "位置", "批次", "到期", "临期",
            "低库存", "缺货", "流水", "入库", "领用", "报损", "提醒", "当前");
    private static final List<String> KNOWLEDGE_TERMS = List.of(
            "怎么", "如何", "清洁", "维护", "保养", "使用", "说明", "故障", "注意", "步骤", "资料");

    private final CatalogApi catalogApi;
    private final InventoryApi inventoryApi;
    private final LocationApi locationApi;

    QaScopePlanner(CatalogApi catalogApi, InventoryApi inventoryApi, LocationApi locationApi) {
        this.catalogApi = catalogApi;
        this.inventoryApi = inventoryApi;
        this.locationApi = locationApi;
    }

    ScopePlan plan(UUID householdId, QaRequest request) {
        QaTarget explicitTarget = request.scope() == null
                ? null
                : authorizeAndLabel(householdId, request.scope());
        QaTarget pageTarget = request.pageContext() == null
                ? null
                : authorizeAndLabel(householdId, request.pageContext());
        List<QaTarget> confirmedTargets = new ArrayList<>();
        addDistinct(confirmedTargets, explicitTarget);
        for (QaTarget confirmedScope : request.confirmedScopes()) {
            addDistinct(confirmedTargets, authorizeAndLabel(householdId, confirmedScope));
        }
        if (confirmedTargets.isEmpty()) addDistinct(confirmedTargets, pageTarget);

        String recommended = recommend(request.question(), pageTarget);
        String requested = normalizeAnswerScope(request.answerScope(), request.scope());
        String used = AUTO.equals(requested) ? recommended : requested;
        QaTarget target = factTarget(used, confirmedTargets);
        QaTarget knowledgeTarget = confirmedTargets.stream()
                .filter(candidate -> isKnowledgeTarget(candidate.type()))
                .findFirst()
                .orElse(null);
        String reason = scopeReason(recommended, used, explicitTarget, pageTarget);

        CandidateResolution resolution = candidates(
                householdId, request.question(), used, confirmedTargets);
        if (!resolution.ambiguous().isEmpty()) {
            return new ScopePlan(recommended, used,
                    "检测到多个同名对象，请确认后再查询",
                    target, knowledgeTarget, resolution.ambiguous(), true);
        }
        if (target == null) {
            List<ScopeCandidate> eligible = eligibleCandidates(used, resolution.unique());
            if (eligible.size() == 1) {
                ScopeCandidate candidate = eligible.getFirst();
                target = new QaTarget(candidate.type(), candidate.id(), candidate.label());
                if (isKnowledgeTarget(target.type())) knowledgeTarget = target;
            } else if (eligible.size() > 1 && (KNOWLEDGE_SOURCE.equals(used) || BOTH.equals(used))) {
                return new ScopePlan(recommended, used,
                        "知识来源一次需要确认一个物品或批次",
                        null, null, eligible, true);
            }
        }

        if ((KNOWLEDGE_SOURCE.equals(used) || BOTH.equals(used)) && knowledgeTarget == null) {
            resolution = candidates(
                    householdId, request.question(), KNOWLEDGE_SOURCE, confirmedTargets);
            if (!resolution.ambiguous().isEmpty()) {
                return new ScopePlan(recommended, used,
                        "检测到多个同名物品或批次，请确认后再查询",
                        target, null, resolution.ambiguous(), true);
            }
            List<ScopeCandidate> eligible = eligibleCandidates(KNOWLEDGE_SOURCE, resolution.unique());
            if (eligible.size() == 1) {
                ScopeCandidate candidate = eligible.getFirst();
                knowledgeTarget = new QaTarget(candidate.type(), candidate.id(), candidate.label());
            } else if (eligible.size() > 1) {
                return new ScopePlan(recommended, used,
                        "知识来源一次需要确认一个物品或批次",
                        target, null, eligible, true);
            }
        }

        if ((KNOWLEDGE_SOURCE.equals(used) || BOTH.equals(used)) && knowledgeTarget == null) {
            return new ScopePlan(recommended, used,
                    "知识来源需要先确认物品或批次范围",
                    target, null, List.of(), true);
        }
        if (KNOWLEDGE_SOURCE.equals(used)) target = knowledgeTarget;
        return new ScopePlan(recommended, used, reason, target, knowledgeTarget, List.of(), false);
    }

    private String recommend(String question, QaTarget pageTarget) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        boolean fact = containsAny(normalized, FACT_TERMS);
        boolean knowledge = containsAny(normalized, KNOWLEDGE_TERMS);
        if (fact && knowledge) return BOTH;
        if (knowledge) return KNOWLEDGE_SOURCE;
        if (fact) return HOUSEHOLD_FACT;
        if (pageTarget != null && isKnowledgeTarget(pageTarget.type())) return BOTH;
        return HOUSEHOLD_FACT;
    }

    private String normalizeAnswerScope(String answerScope, QaTarget legacyScope) {
        if (answerScope == null || answerScope.isBlank()) {
            return legacyScope == null ? HOUSEHOLD_FACT : KNOWLEDGE_SOURCE;
        }
        String normalized = answerScope.trim().toUpperCase(Locale.ROOT);
        if (!List.of(AUTO, HOUSEHOLD_FACT, KNOWLEDGE_SOURCE, BOTH).contains(normalized)) {
            throw new IllegalArgumentException("回答范围仅支持 AUTO、HOUSEHOLD_FACT、KNOWLEDGE_SOURCE 或 BOTH");
        }
        return normalized;
    }

    private QaTarget authorizeAndLabel(UUID householdId, QaTarget scope) {
        return switch (scope.type()) {
            case "ITEM" -> {
                String label = catalogApi.itemNames(householdId, List.of(scope.id())).get(scope.id());
                if (label == null) throw new IllegalArgumentException("物品不存在或不属于当前家庭");
                yield new QaTarget(scope.type(), scope.id(), label);
            }
            case "LOT" -> {
                InventoryApi.LotFlat lot = inventoryApi.findLot(householdId, scope.id())
                        .orElseThrow(() -> new IllegalArgumentException("批次不存在或不属于当前家庭"));
                String itemName = catalogApi.itemNames(householdId, List.of(lot.itemId()))
                        .getOrDefault(lot.itemId(), "物品");
                String lotLabel = lot.lotNumber() == null || lot.lotNumber().isBlank()
                        ? itemName + "的批次" : itemName + " · " + lot.lotNumber();
                yield new QaTarget(scope.type(), scope.id(), lotLabel);
            }
            case "LOCATION" -> {
                var location = locationApi.requireLocation(householdId, scope.id());
                yield new QaTarget(scope.type(), scope.id(), location.name());
            }
            default -> throw new IllegalArgumentException("不支持的问答目标类型");
        };
    }

    private CandidateResolution candidates(
            UUID householdId,
            String question,
            String answerScope,
            List<QaTarget> confirmedTargets
    ) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        Map<String, List<ScopeCandidate>> groups = new LinkedHashMap<>();
        for (var item : catalogApi.listActiveItems(householdId)) {
            if (questionContainsTerm(normalized, item.name())) {
                addCandidate(groups, "ITEM:" + item.name().toLowerCase(Locale.ROOT),
                        new ScopeCandidate("ITEM", item.id(), item.name(),
                                "物品 · " + ("DURABLE".equals(item.managementType()) ? "耐用品" : "消耗品")
                                        + " · 编号 " + shortId(item.id())));
            }
            for (var lotInfo : inventoryApi.lotsOfItem(householdId, item.id())) {
                inventoryApi.findLot(householdId, lotInfo.lotId()).ifPresent(lot -> {
                    boolean lotNumberMatches = questionContainsTerm(normalized, lot.lotNumber());
                    boolean serialMatches = questionContainsTerm(normalized, lot.serialNumber());
                    if (lotNumberMatches || serialMatches) {
                        String label = lot.lotNumber() == null || lot.lotNumber().isBlank()
                                ? item.name() + "的批次" : item.name() + " · " + lot.lotNumber();
                        String detail = (lot.serialNumber() == null || lot.serialNumber().isBlank()
                                ? "批次 · " + label : "批次 · " + label + " · 序列号 " + lot.serialNumber())
                                + " · 编号 " + shortId(lot.lotId());
                        String matchedKey = serialMatches
                                ? "LOT_SERIAL:" + lot.serialNumber().toLowerCase(Locale.ROOT)
                                : "LOT_NUMBER:" + lot.lotNumber().toLowerCase(Locale.ROOT);
                        addCandidate(groups, matchedKey, new ScopeCandidate("LOT", lot.lotId(), label, detail));
                    }
                });
            }
        }
        collectLocations(locationApi.tree(householdId).roots(), "", normalized, groups);

        List<ScopeCandidate> unique = new ArrayList<>();
        for (List<ScopeCandidate> group : groups.values()) {
            List<ScopeCandidate> relevant = group.stream()
                    .filter(candidate -> isRelevantCandidate(answerScope, candidate))
                    .toList();
            if (relevant.size() > 1) {
                ScopeCandidate confirmed = relevant.stream()
                        .filter(candidate -> containsTarget(confirmedTargets, candidate))
                        .findFirst()
                        .orElse(null);
                if (confirmed == null) {
                    return new CandidateResolution(relevant, List.copyOf(unique));
                }
                unique.add(confirmed);
            }
            if (relevant.size() == 1) unique.add(relevant.getFirst());
        }
        return new CandidateResolution(List.of(), List.copyOf(unique));
    }

    private void collectLocations(
            List<LocationApi.LocationNode> nodes,
            String prefix,
            String question,
            Map<String, List<ScopeCandidate>> groups
    ) {
        for (var node : nodes) {
            String path = prefix.isBlank() ? node.name() : prefix + " / " + node.name();
            if (questionContainsTerm(question, node.name())) {
                addCandidate(groups, "LOCATION:" + node.name().toLowerCase(Locale.ROOT),
                        new ScopeCandidate("LOCATION", node.id(), node.name(),
                                "位置 · " + path + " · 编号 " + shortId(node.id())));
            }
            collectLocations(node.children(), path, question, groups);
        }
    }

    private static void addCandidate(
            Map<String, List<ScopeCandidate>> groups,
            String key,
            ScopeCandidate candidate
    ) {
        groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(candidate);
    }

    private static boolean questionContainsTerm(String question, String term) {
        return term != null && !term.isBlank()
                && question.contains(term.toLowerCase(Locale.ROOT));
    }

    private static boolean containsAny(String value, List<String> terms) {
        return terms.stream().anyMatch(value::contains);
    }

    private static boolean isKnowledgeTarget(ScopeCandidate candidate) {
        return isKnowledgeTarget(candidate.type());
    }

    private static boolean isRelevantCandidate(String answerScope, ScopeCandidate candidate) {
        return !KNOWLEDGE_SOURCE.equals(answerScope) || isKnowledgeTarget(candidate);
    }

    private static List<ScopeCandidate> eligibleCandidates(String used, List<ScopeCandidate> candidates) {
        List<ScopeCandidate> knowledgeTargets = candidates.stream()
                .filter(QaScopePlanner::isKnowledgeTarget)
                .toList();
        List<ScopeCandidate> lots = knowledgeTargets.stream()
                .filter(candidate -> "LOT".equals(candidate.type()))
                .toList();
        if (lots.size() == 1) return lots;
        if (KNOWLEDGE_SOURCE.equals(used) || BOTH.equals(used)) return knowledgeTargets;
        List<ScopeCandidate> locations = candidates.stream()
                .filter(candidate -> "LOCATION".equals(candidate.type()))
                .toList();
        if (locations.size() == 1 && knowledgeTargets.size() == 1) return locations;
        boolean remainingAreLocations = candidates.stream()
                .filter(candidate -> !knowledgeTargets.contains(candidate))
                .allMatch(candidate -> "LOCATION".equals(candidate.type()));
        if (knowledgeTargets.size() == 1 && remainingAreLocations) {
            return knowledgeTargets;
        }
        return candidates;
    }

    private static boolean isKnowledgeTarget(String type) {
        return "ITEM".equals(type) || "LOT".equals(type);
    }

    private static QaTarget factTarget(String used, List<QaTarget> confirmedTargets) {
        if (confirmedTargets.isEmpty()) return null;
        if (HOUSEHOLD_FACT.equals(used) || BOTH.equals(used)) {
            QaTarget location = confirmedTargets.stream()
                    .filter(candidate -> "LOCATION".equals(candidate.type()))
                    .findFirst()
                    .orElse(null);
            if (location != null) return location;
        }
        return confirmedTargets.getFirst();
    }

    private static boolean containsTarget(List<QaTarget> targets, ScopeCandidate candidate) {
        return targets.stream().anyMatch(target -> target.type().equals(candidate.type())
                && target.id().equals(candidate.id()));
    }

    private static void addDistinct(List<QaTarget> targets, QaTarget candidate) {
        if (candidate == null) return;
        boolean exists = targets.stream().anyMatch(target -> target.type().equals(candidate.type())
                && target.id().equals(candidate.id()));
        if (!exists) targets.add(candidate);
    }

    private static String shortId(UUID id) {
        String value = id.toString();
        return value.substring(value.length() - 8);
    }

    private static String scopeReason(
            String recommended,
            String used,
            QaTarget explicitTarget,
            QaTarget pageTarget
    ) {
        if (explicitTarget != null) return "已使用你确认的回答目标和来源范围";
        if (pageTarget != null) return "根据问题和当前页面推荐回答范围";
        if (!recommended.equals(used)) return "已使用你调整后的来源范围";
        return "根据问题内容推荐回答范围";
    }

    private record CandidateResolution(List<ScopeCandidate> ambiguous, List<ScopeCandidate> unique) {
    }
}
