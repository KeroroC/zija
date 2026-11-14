package com.zija.ai.internal;

import java.util.EnumSet;
import java.util.Set;

/**
 * 家庭事实问答工具意图标签。词表兜底与 Jev 预路由共用同一枚举。
 */
enum HouseholdFactToolIntent {
    GENERAL(HouseholdFactToolNames.ALL),
    ITEM_STOCK(Set.of(
            HouseholdFactToolNames.SEARCH_ITEMS,
            HouseholdFactToolNames.SEARCH_LOTS,
            HouseholdFactToolNames.ITEM_STOCK)),
    LOCATION_STOCK(Set.of(
            HouseholdFactToolNames.SEARCH_LOCATIONS,
            HouseholdFactToolNames.LOCATION_STOCK,
            HouseholdFactToolNames.ITEM_MOVEMENTS)),
    MOVEMENTS(Set.of(
            HouseholdFactToolNames.ITEM_MOVEMENTS,
            HouseholdFactToolNames.SEARCH_ITEMS,
            HouseholdFactToolNames.ITEM_STOCK)),
    EXPIRY(Set.of(
            HouseholdFactToolNames.EXPIRING_LOTS,
            HouseholdFactToolNames.EXPIRED_LOTS)),
    LOW_STOCK(Set.of(HouseholdFactToolNames.LOW_STOCK, HouseholdFactToolNames.SEARCH_ITEMS)),
    REMINDERS(Set.of(HouseholdFactToolNames.OPEN_REMINDER_TASKS));

    private final Set<String> toolNames;

    HouseholdFactToolIntent(Set<String> toolNames) {
        this.toolNames = toolNames;
    }

    Set<String> toolNames() {
        return toolNames;
    }

    static HouseholdFactToolIntent fromJevChoice(String choice) {
        if (choice == null || choice.isBlank()) {
            return GENERAL;
        }
        try {
            return HouseholdFactToolIntent.valueOf(choice);
        } catch (IllegalArgumentException exception) {
            return GENERAL;
        }
    }

    static Set<HouseholdFactToolIntent> jevCriteriaKeys() {
        return EnumSet.complementOf(EnumSet.of(GENERAL));
    }
}
