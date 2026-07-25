package com.zija.inventory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 库存变更公开事件。每个成功库存命令发布一条，带全局唯一 eventId 供消费者去重。
 * 阶段四只建立发布契约，不实现阶段五提醒消费者。
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
        UUID idempotencyKey
) {}
