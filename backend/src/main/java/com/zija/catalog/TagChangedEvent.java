package com.zija.catalog;

import java.util.UUID;

/**
 * 标签变更公开事件。changeType: CREATED / UPDATED / ARCHIVED / RESTORED。
 */
public record TagChangedEvent(
        UUID eventId,
        UUID householdId,
        UUID tagId,
        String changeType
) {}
