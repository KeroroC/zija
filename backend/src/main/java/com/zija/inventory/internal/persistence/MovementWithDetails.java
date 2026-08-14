package com.zija.inventory.internal.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 库存流水详情 DTO，附带物品名、单位、来源/目标位置名、操作人显示名。
 * 用于流水分页列表查询，直接映射 SQL 查询结果。
 */
public record MovementWithDetails(
        UUID id,
        UUID householdId,
        UUID lotId,
        UUID itemId,
        String type,
        BigDecimal quantity,
        UUID fromLocationId,
        UUID toLocationId,
        String reason,
        String memo,
        UUID operatorAccountId,
        OffsetDateTime businessTime,
        OffsetDateTime createdAt,
        String idempotencyKey,
        UUID reversalOf,
        String itemName,
        String unitName,
        String fromLocationName,
        String toLocationName,
        String operatorDisplayName
) {}
