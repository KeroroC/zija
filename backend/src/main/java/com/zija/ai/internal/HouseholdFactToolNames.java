package com.zija.ai.internal;

import java.util.Set;

/** Spring AI 注册的家庭事实工具名（与方法名一致）。 */
final class HouseholdFactToolNames {

    static final String SEARCH_ITEMS = "searchItems";
    static final String SEARCH_LOTS = "searchLots";
    static final String ITEM_STOCK = "itemStock";
    static final String SEARCH_LOCATIONS = "searchLocations";
    static final String LOCATION_STOCK = "locationStock";
    static final String EXPIRING_LOTS = "expiringLots";
    static final String EXPIRED_LOTS = "expiredLots";
    static final String LOW_STOCK = "lowStock";
    static final String OPEN_REMINDER_TASKS = "openReminderTasks";
    static final String ITEM_MOVEMENTS = "itemMovements";

    static final Set<String> ALL = Set.of(
            SEARCH_ITEMS,
            SEARCH_LOTS,
            ITEM_STOCK,
            SEARCH_LOCATIONS,
            LOCATION_STOCK,
            EXPIRING_LOTS,
            EXPIRED_LOTS,
            LOW_STOCK,
            OPEN_REMINDER_TASKS,
            ITEM_MOVEMENTS);

    private HouseholdFactToolNames() {
    }
}
