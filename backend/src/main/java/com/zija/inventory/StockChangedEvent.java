package com.zija.inventory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 库存变更公开事件。每个成功库存命令发布一条，带全局唯一 eventId 供消费者去重。
 * 字段只追加、不重排、不删除——跨模块契约（ADR-006）。
 */
public record StockChangedEvent(
        UUID eventId,
        UUID householdId,
        UUID lotId,
        UUID itemId,
        String movementType,
        BigDecimal quantityDelta,
        UUID fromLocationId,
        UUID toLocationId,
        OffsetDateTime businessTime,
        UUID movementId,
        UUID idempotencyKey,
        // 阶段六追加（ADR-006）：
        UUID operatorAccountId,
        String reason,
        UUID reversalOf
) {}
