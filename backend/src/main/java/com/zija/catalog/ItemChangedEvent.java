package com.zija.catalog;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 物品变更公开事件。changeType: CREATED / UPDATED / ARCHIVED / RESTORED。
 * 字段只追加、不重排、不删除——跨模块契约。
 */
public record ItemChangedEvent(
        UUID eventId,
        UUID householdId,
        UUID itemId,
        String changeType,
        OffsetDateTime businessTime
) {}
