package com.zija.ai.internal;

import com.zija.ai.internal.HouseholdFactQaModels.QaTarget;
import com.zija.inventory.InventoryApi;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** 结构化事实兜底与 Jev 预路由共用的确定性意图识别。 */
final class HouseholdFactIntentLexicon {

    private HouseholdFactIntentLexicon() {
    }

    static HouseholdFactToolIntent detect(String question, QaTarget target) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (target != null && "LOCATION".equals(target.type())) {
            if (asksLocationMovements(normalized)) {
                return HouseholdFactToolIntent.MOVEMENTS;
            }
            return HouseholdFactToolIntent.LOCATION_STOCK;
        }
        if (containsAny(normalized, "流水", "入库", "领用", "报损", "变化", "操作人")
                && target != null) {
            return HouseholdFactToolIntent.MOVEMENTS;
        }
        if (asksPendingReminders(normalized)) {
            return HouseholdFactToolIntent.REMINDERS;
        }
        if (asksSoonExpiring(normalized)) {
            return HouseholdFactToolIntent.EXPIRY;
        }
        if (asksExpiredLots(normalized)) {
            return HouseholdFactToolIntent.EXPIRY;
        }
        if (containsAny(normalized, "低库存", "缺货", "短缺")) {
            return HouseholdFactToolIntent.LOW_STOCK;
        }
        if (target != null) {
            return HouseholdFactToolIntent.ITEM_STOCK;
        }
        return HouseholdFactToolIntent.GENERAL;
    }

    static void runStructuredFallback(
            HouseholdFactTools tools,
            String question,
            UUID householdId,
            QaTarget target,
            InventoryApi inventoryApi,
            HouseholdFactQaModels.Collector collector
    ) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);

        if (target != null && "LOCATION".equals(target.type())) {
            if (asksLocationMovements(normalized)) {
                tools.itemMovements(null, 10, null, null, null, null);
            } else {
                tools.locationStock(null, "", 10);
            }
            return;
        }
        if (containsAny(normalized, "流水", "入库", "领用", "报损", "变化", "操作人")
                && target != null) {
            fallbackItemId(householdId, target, inventoryApi).ifPresentOrElse(
                    itemId -> tools.itemMovements(itemId.toString(), 10, null, null, null, null),
                    () -> collector.markFactSourceUnavailable());
            return;
        }
        if (asksPendingReminders(normalized)) {
            tools.openReminderTasks(10);
            return;
        }
        if (asksSoonExpiring(normalized)) {
            tools.expiringLots(30, 10);
            return;
        }
        if (asksExpiredLots(normalized)) {
            tools.expiredLots(10);
            return;
        }
        if (containsAny(normalized, "低库存", "缺货", "短缺")) {
            tools.lowStock(10);
            return;
        }
        if (target != null) {
            fallbackItemId(householdId, target, inventoryApi).ifPresentOrElse(
                    itemId -> tools.itemStock(itemId.toString(), null, 10),
                    () -> collector.markFactSourceUnavailable());
            return;
        }
        tools.searchItems("", 10);
    }

    private static Optional<UUID> fallbackItemId(UUID householdId, QaTarget target, InventoryApi inventoryApi) {
        if (target == null) {
            return Optional.empty();
        }
        if ("ITEM".equals(target.type())) {
            return Optional.of(target.id());
        }
        if ("LOT".equals(target.type())) {
            return inventoryApi.findLot(householdId, target.id()).map(InventoryApi.LotFlat::itemId);
        }
        return Optional.empty();
    }

    private static boolean asksLocationMovements(String question) {
        return containsAny(question, "流水", "进出", "入库", "领用", "报损");
    }

    private static boolean asksSoonExpiring(String question) {
        return containsAny(question, "快过期", "临期", "到期") && !question.contains("已经过期");
    }

    private static boolean asksExpiredLots(String question) {
        return question.contains("已经过期") || question.contains("过期");
    }

    private static boolean asksPendingReminders(String question) {
        if (!question.contains("提醒")) {
            return false;
        }
        return !containsAny(question, "提醒规则", "怎么设置", "如何设置", "系统配置");
    }

    private static boolean containsAny(String value, String... terms) {
        return java.util.Arrays.stream(terms).anyMatch(value::contains);
    }
}
