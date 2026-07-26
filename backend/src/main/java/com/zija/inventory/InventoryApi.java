package com.zija.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 库存模块公共 API，提供库存位、批次流水只读查询与公开库存变更事件类型。
 * 仅暴露记录类型与查询端口，不含命令；命令由本模块 REST 端点接收。
 */
public interface InventoryApi {

    Optional<StockPositionInfo> findStockPosition(UUID householdId, UUID lotId, UUID locationId);

    List<StockPositionInfo> stockPositionsOfItem(UUID householdId, UUID itemId);

    List<MovementInfo> movementsOfLot(UUID householdId, UUID lotId);

    /** 列出某物品所有批次含到期日与当前总库存（聚合各位置）。 */
    List<LotInfo> lotsOfItem(UUID householdId, UUID itemId);

    /** 某物品当前总库存（聚合各位置）。 */
    BigDecimal currentTotalStockOfItem(UUID householdId, UUID itemId);

    record StockPositionInfo(
            UUID lotId,
            UUID locationId,
            BigDecimal quantity,
            long revision,
            OffsetDateTime updatedAt
    ) {}

    record MovementInfo(
            UUID id,
            UUID lotId,
            UUID itemId,
            String type,
            BigDecimal quantity,
            UUID fromLocationId,
            UUID toLocationId,
            String reason,
            UUID operatorAccountId,
            OffsetDateTime businessTime,
            OffsetDateTime createdAt,
            UUID idempotencyKey,
            UUID reversalOf
    ) {}

    record LotInfo(
            UUID lotId,
            UUID itemId,
            LocalDate expiryDate,
            BigDecimal totalQuantity
    ) {}
}
