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

    /** 按家庭范围查找批次元数据（批次号/序列号/到期日）。不存在或家庭不匹配返回 empty。 */
    Optional<LotFlat> findLot(UUID householdId, UUID lotId);

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

    /** 批次元数据（仅供 reporting 投影使用批次号/序列号/到期日）。 */
    record LotFlat(
            UUID lotId,
            UUID itemId,
            String lotNumber,
            String serialNumber,
            LocalDate expiryDate
    ) {}

    /** 增量拉取家庭库存位（按 updated_at 游标分批）。仅供 reporting 投影重建。 */
    PageDump<StockPositionDump> dumpStockPositions(UUID householdId, OffsetDateTime cursor, int limit);

    /** 增量拉取家庭全部库存流水（按 created_at 游标分批）。仅供 reporting 投影重建。 */
    PageDump<MovementDump> dumpMovements(UUID householdId, OffsetDateTime cursor, int limit);

    /** 分页拉取结果，游标为最后一条的排序字段值。 */
    record PageDump<T>(List<T> items, OffsetDateTime nextCursor, boolean hasMore) {}

    /** 库存位快照 DTO（仅供 dump）。 */
    record StockPositionDump(
            UUID lotId,
            UUID itemId,
            UUID locationId,
            BigDecimal quantity,
            OffsetDateTime updatedAt
    ) {}

    /** 库存流水快照 DTO（仅供 dump）。 */
    record MovementDump(
            UUID id,
            UUID lotId,
            UUID itemId,
            String type,
            BigDecimal quantityDelta,
            UUID fromLocationId,
            UUID toLocationId,
            String reason,
            UUID operatorAccountId,
            UUID reversalOf,
            OffsetDateTime businessTime,
            OffsetDateTime createdAt
    ) {}
}
