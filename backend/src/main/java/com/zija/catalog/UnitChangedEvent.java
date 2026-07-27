package com.zija.catalog;

import java.util.UUID;

/**
 * 计量单位变更公开事件。changeType: CREATED / UPDATED / ARCHIVED / RESTORED。
 */
public record UnitChangedEvent(
        UUID eventId,
        UUID householdId,
        UUID unitId,
        String changeType
) {}
