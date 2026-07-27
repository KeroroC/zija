package com.zija.catalog;

import java.util.UUID;

/**
 * 分类变更公开事件。changeType: CREATED / UPDATED / ARCHIVED / RESTORED / MOVED。
 */
public record CategoryChangedEvent(
        UUID eventId,
        UUID householdId,
        UUID categoryId,
        String changeType
) {}
