package com.zija.location;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 位置变更公开事件。changeType: CREATED / UPDATED / RENAMED / MOVED / DELETED。
 * 字段只追加、不重排、不删除——跨模块契约。
 */
public record LocationChangedEvent(
        UUID eventId,
        UUID householdId,
        UUID locationId,
        String changeType,
        UUID parentId,
        OffsetDateTime businessTime
) {}
