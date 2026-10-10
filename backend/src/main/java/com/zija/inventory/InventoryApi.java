package com.zija.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
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

    /** 列出某物品所有批次含到期日、当前总库存（聚合各位置）以及批次号/序列号。 */
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
            BigDecimal totalQuantity,
            String lotNumber,
            String serialNumber
    ) {}

    /**
     * 批次号或序列号作为子串出现在 question 中的批次（仅非空项；空白序列号不得命中无关问题）。
     * 只返回当前家庭、所属物品为 ACTIVE 的批次。
     */
    List<LotQuestionMatch> findLotsMatchingQuestion(UUID householdId, String question, int limit);

    /**
     * 按批次号或序列号关键字有界搜索当前家庭、所属物品为 ACTIVE 的批次。
     * 关键字按字面子串匹配，{@code %} 与 {@code _} 不是通配符。
     * 空白批次号与空白序列号不参与匹配，因此无关问题不会因空序列号命中。
     * 关键字为空白时只返回有界前 {@code limit} 条。
     * {@code itemId}、{@code lotId} 非空时再收窄到该物品或该批次。
     */
    List<LotQuestionMatch> searchLotsByNumberOrSerial(
            UUID householdId,
            String keyword,
            UUID itemId,
            UUID lotId,
            int limit
    );

    /**
     * 按批次号或序列号搜索，且只保留在 {@code locationIds} 中数量为正的批次。
     * 空集合或 null 返回空列表，不跨家庭。关键字规则与 {@link #searchLotsByNumberOrSerial} 相同。
     */
    List<LotQuestionMatch> searchLotsByNumberOrSerialInLocations(
            UUID householdId,
            String keyword,
            Collection<UUID> locationIds,
            int limit
    );

    /**
     * 临期批次：{@code today <= expiry <= horizon} 且数量为正。日期由调用方按 AI Clock 传入，本模块不自造时区。
     */
    List<LotQuantitySnapshot> findExpiringLots(
            UUID householdId,
            LocalDate today,
            LocalDate horizon,
            UUID itemId,
            UUID lotId,
            int limit
    );

    /**
     * 临期批次，数量只累计 {@code locationIds} 中的库存位。空集合返回空列表，不跨家庭。
     */
    List<LotQuantitySnapshot> findExpiringLotsInLocations(
            UUID householdId,
            LocalDate today,
            LocalDate horizon,
            Collection<UUID> locationIds,
            int limit
    );

    /**
     * 已过期批次：{@code expiry < today} 且数量为正。日期由调用方传入。
     */
    List<LotQuantitySnapshot> findExpiredLots(
            UUID householdId,
            LocalDate today,
            UUID itemId,
            UUID lotId,
            int limit
    );

    /**
     * 已过期批次，数量只累计 {@code locationIds} 中的库存位。空集合返回空列表，不跨家庭。
     */
    List<LotQuantitySnapshot> findExpiredLotsInLocations(
            UUID householdId,
            LocalDate today,
            Collection<UUID> locationIds,
            int limit
    );

    /**
     * 低库存：ACTIVE 且 {@code low_stock_mode='CUSTOM'}、当前总量低于阈值。
     */
    List<LowStockSnapshot> findLowStockItems(UUID householdId, UUID itemId, int limit);

    /**
     * 指定位置集合（含子位置 id）中的当前库存位，可选物品名称关键字，有界。
     * 关键字按字面子串匹配，{@code %} 与 {@code _} 不是通配符。
     */
    List<LocationStockPositionSnapshot> findStockPositionsInLocations(
            UUID householdId,
            Collection<UUID> locationIds,
            String itemNameContains,
            int limit
    );

    /**
     * 有界流水。物品、批次、位置（来源或目标任一命中）、业务时间窗均可选。
     * 物品与位置都未给出时返回空列表，不扫全家流水。
     * {@code locationIds} 为空集合时返回空列表；为 null 时不按位置过滤。
     * {@code fromInclusive} / {@code toExclusive} 为 null 时不限制对应一侧。
     */
    List<MovementInfo> findMovements(
            UUID householdId,
            UUID itemId,
            UUID lotId,
            Collection<UUID> locationIds,
            OffsetDateTime fromInclusive,
            OffsetDateTime toExclusive,
            int limit
    );

    record LotQuestionMatch(
            UUID lotId,
            UUID itemId,
            String itemName,
            String lotNumber,
            String serialNumber
    ) {}

    record LotQuantitySnapshot(
            UUID lotId,
            UUID itemId,
            String itemName,
            String lotNumber,
            LocalDate expiryDate,
            BigDecimal quantity,
            String unitName
    ) {}

    record LowStockSnapshot(
            UUID itemId,
            String itemName,
            String unitName,
            BigDecimal currentTotal,
            BigDecimal threshold
    ) {}

    record LocationStockPositionSnapshot(
            UUID itemId,
            String itemName,
            String unitName,
            UUID lotId,
            String lotNumber,
            UUID locationId,
            BigDecimal quantity,
            LocalDate expiryDate
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
