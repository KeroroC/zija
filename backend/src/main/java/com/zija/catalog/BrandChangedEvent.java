package com.zija.catalog;

import java.util.UUID;

/**
 * 品牌变更公开事件。changeType: CREATED / UPDATED / ARCHIVED / RESTORED。
 */
public record BrandChangedEvent(
        UUID eventId,
        UUID householdId,
        UUID brandId,
        String changeType
) {}
