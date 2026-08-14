package com.zija.inventory.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zija.household.HouseholdApi;
import com.zija.inventory.InventoryApi;
import com.zija.inventory.internal.persistence.LotEntity;
import com.zija.inventory.internal.persistence.ItemStockAggregateMapper;
import com.zija.inventory.internal.persistence.LotMapper;
import com.zija.inventory.internal.persistence.MovementMapper;
import com.zija.inventory.internal.persistence.StockPositionEntity;
import com.zija.inventory.internal.persistence.StockPositionMapper;
import com.zija.inventory.internal.persistence.StocktakeItemWithDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 库存编排层 —— 命令入口与 {@link InventoryApi} 只读端口实现。
 * <p>
 * 所有命令操作先通过 {@link HouseholdApi} 校验当前账户的成员身份与角色，
 * 再委托给对应的专业服务。权限校验在事务之外，各服务自行管理事务边界。
 * <ul>
 *   <li>普通命令（入库、领用、报损、移位、批次管理）：{@code requireActiveMember}</li>
 *   <li>冲正、一致性检查：额外要求 {@code hasAtLeastRole(ADMIN)}</li>
 * </ul>
 * 审计记录与幂等控制由各专业服务内部完成，本层不重复。
 */
@Service
class InventoryService implements InventoryApi {

    private final HouseholdApi householdApi;
    private final LotService lotService;
    private final StockCommandService stockCommandService;
    private final ReversalService reversalService;
    private final ConsistencyCheckService consistencyCheckService;
    private final StocktakeService stocktakeService;
    private final LotMapper lotMapper;
    private final MovementMapper movementMapper;
    private final StockPositionMapper stockPositionMapper;
    private final ItemStockAggregateMapper itemStockAggregateMapper;

    InventoryService(HouseholdApi householdApi,
                     LotService lotService,
                     StockCommandService stockCommandService,
                     ReversalService reversalService,
                     ConsistencyCheckService consistencyCheckService,
                     StocktakeService stocktakeService,
                     LotMapper lotMapper,
                     MovementMapper movementMapper,
                     StockPositionMapper stockPositionMapper,
                     ItemStockAggregateMapper itemStockAggregateMapper) {
        this.householdApi = householdApi;
        this.lotService = lotService;
        this.stockCommandService = stockCommandService;
        this.reversalService = reversalService;
        this.consistencyCheckService = consistencyCheckService;
        this.stocktakeService = stocktakeService;
        this.lotMapper = lotMapper;
        this.movementMapper = movementMapper;
        this.stockPositionMapper = stockPositionMapper;
        this.itemStockAggregateMapper = itemStockAggregateMapper;
    }

    // ---- InventoryApi read-only ports ----

    @Override
    public Optional<StockPositionInfo> findStockPosition(UUID householdId, UUID lotId, UUID locationId) {
        var wrapper = new LambdaQueryWrapper<StockPositionEntity>()
                .eq(StockPositionEntity::getHouseholdId, householdId)
                .eq(StockPositionEntity::getLotId, lotId)
                .eq(StockPositionEntity::getLocationId, locationId);
        return Optional.ofNullable(stockPositionMapper.selectOne(wrapper))
                .map(sp -> new StockPositionInfo(
                        sp.getLotId(), sp.getLocationId(),
                        sp.getQuantity(), sp.getRevision(), sp.getUpdatedAt()));
    }

    @Override
    public List<StockPositionInfo> stockPositionsOfItem(UUID householdId, UUID itemId) {
        // 先查该物品的批次 ID 列表，再查对应库存位
        var lotWrapper = new LambdaQueryWrapper<LotEntity>()
                .eq(LotEntity::getHouseholdId, householdId)
                .eq(LotEntity::getItemId, itemId)
                .select(LotEntity::getId);
        List<UUID> lotIds = lotMapper.selectList(lotWrapper).stream()
                .map(LotEntity::getId)
                .toList();
        if (lotIds.isEmpty()) {
            return List.of();
        }
        var spWrapper = new LambdaQueryWrapper<StockPositionEntity>()
                .eq(StockPositionEntity::getHouseholdId, householdId)
                .in(StockPositionEntity::getLotId, lotIds);
        return stockPositionMapper.selectList(spWrapper).stream()
                .map(sp -> new StockPositionInfo(
                        sp.getLotId(), sp.getLocationId(),
                        sp.getQuantity(), sp.getRevision(), sp.getUpdatedAt()))
                .toList();
    }

    @Override
    public List<MovementInfo> movementsOfLot(UUID householdId, UUID lotId) {
        return movementMapper.findByLot(householdId, lotId).stream()
                .map(m -> new MovementInfo(
                        m.getId(), m.getLotId(), m.getItemId(), m.getType(),
                        m.getQuantity(), m.getFromLocationId(), m.getToLocationId(),
                        m.getReason(), m.getOperatorAccountId(), m.getBusinessTime(),
                        m.getCreatedAt(), UUID.fromString(m.getIdempotencyKey()),
                        m.getReversalOf()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LotFlat> findLot(UUID householdId, UUID lotId) {
        var wrapper = new LambdaQueryWrapper<LotEntity>()
                .eq(LotEntity::getHouseholdId, householdId)
                .eq(LotEntity::getId, lotId);
        return Optional.ofNullable(lotMapper.selectOne(wrapper))
                .map(l -> new LotFlat(l.getId(), l.getItemId(),
                        l.getLotNumber(), l.getSerialNumber(), l.getExpiryDate()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LotInfo> lotsOfItem(UUID householdId, UUID itemId) {
        return itemStockAggregateMapper.lotsOfItem(householdId, itemId).stream()
                .map(r -> new LotInfo(r.getLotId(), r.getItemId(), r.getExpiryDate(), r.getTotalQuantity()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal currentTotalStockOfItem(UUID householdId, UUID itemId) {
        var v = itemStockAggregateMapper.totalStockOfItem(householdId, itemId);
        return v != null ? v : BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public PageDump<StockPositionDump> dumpStockPositions(UUID householdId, OffsetDateTime cursor, int limit) {
        var items = stockPositionMapper.dumpStockPositions(householdId, cursor, limit);
        OffsetDateTime nextCursor = items.isEmpty() ? cursor : items.get(items.size() - 1).updatedAt();
        boolean hasMore = items.size() == limit;
        return new PageDump<>(items, nextCursor, hasMore);
    }

    @Override
    @Transactional(readOnly = true)
    public PageDump<MovementDump> dumpMovements(UUID householdId, OffsetDateTime cursor, int limit) {
        var items = movementMapper.dumpMovements(householdId, cursor, limit);
        OffsetDateTime nextCursor = items.isEmpty() ? cursor : items.get(items.size() - 1).createdAt();
        boolean hasMore = items.size() == limit;
        return new PageDump<>(items, nextCursor, hasMore);
    }

    // ---- Lot commands ----

    /**
     * 更新批次元数据。
     */
    @Transactional
    public LotEntity updateLotMeta(UUID accountId, UUID householdId, UUID lotId,
                                   int clientVersion, LocalDate purchaseDate,
                                   LocalDate productionDate, LocalDate expiryDate,
                                   String serialNumber, String memo) {
        householdApi.requireActiveMember(accountId);
        return lotService.updateLotMeta(householdId, lotId, clientVersion,
                purchaseDate, productionDate, expiryDate,
                serialNumber, memo);
    }

    // ---- Stock commands ----

    /**
     * 新建批次入库。
     */
    public StockCommandService.InboundResult inboundNewLot(UUID accountId, UUID householdId,
                                                           UUID locationId,
                                                           StockCommandService.InboundNewLotCommand cmd) {
        householdApi.requireActiveMember(accountId);
        return stockCommandService.inboundNewLot(householdId, accountId, locationId, cmd);
    }

    /**
     * 现有批次入库。
     */
    public StockCommandService.InboundResult inboundExistingLot(UUID accountId, UUID householdId,
                                                                UUID locationId, UUID lotId,
                                                                BigDecimal quantity, String memo,
                                                                String idempotencyKey) {
        householdApi.requireActiveMember(accountId);
        return stockCommandService.inboundExistingLot(householdId, accountId, locationId,
                lotId, quantity, memo, idempotencyKey);
    }

    /**
     * 领用（消耗库存）。
     */
    public StockCommandService.InboundResult consume(UUID accountId, UUID householdId,
                                                     UUID lotId, UUID locationId,
                                                     BigDecimal quantity, String reason,
                                                     String memo, String idempotencyKey) {
        householdApi.requireActiveMember(accountId);
        return stockCommandService.consume(householdId, accountId, lotId, locationId,
                quantity, reason, memo, idempotencyKey);
    }

    /**
     * 报损（报废/过期等损耗）。
     */
    public StockCommandService.InboundResult loss(UUID accountId, UUID householdId,
                                                  UUID lotId, UUID locationId,
                                                  BigDecimal quantity, String reason,
                                                  String memo, String idempotencyKey) {
        householdApi.requireActiveMember(accountId);
        return stockCommandService.loss(householdId, accountId, lotId, locationId,
                quantity, reason, memo, idempotencyKey);
    }

    /**
     * 移位（库存转移）。
     */
    public StockCommandService.InboundResult transfer(UUID accountId, UUID householdId,
                                                      UUID lotId, UUID fromLocationId,
                                                      UUID toLocationId, BigDecimal quantity,
                                                      String memo, String idempotencyKey) {
        householdApi.requireActiveMember(accountId);
        return stockCommandService.transfer(householdId, accountId, lotId,
                fromLocationId, toLocationId, quantity, memo, idempotencyKey);
    }

    // ---- Stocktake commands ----

    /**
     * 创建盘点草稿。所有活跃成员可执行。
     */
    public UUID createStocktakeDraft(UUID accountId, UUID householdId, UUID locationId) {
        householdApi.requireActiveMember(accountId);
        return stocktakeService.createDraft(householdId, accountId, locationId);
    }

    /**
     * 更新盘点草稿行项。所有活跃成员可执行。
     */
    public void updateStocktakeDraft(UUID accountId, UUID householdId, UUID stocktakeId,
                                     int clientVersion, List<StocktakeService.StocktakeItemUpdate> updates) {
        householdApi.requireActiveMember(accountId);
        stocktakeService.updateDraft(householdId, stocktakeId, clientVersion, updates);
    }

    /**
     * 刷新盘点草稿快照。所有活跃成员可执行。
     */
    public void refreshStocktakeDraft(UUID accountId, UUID householdId, UUID stocktakeId,
                                      int clientVersion, UUID locationId) {
        householdApi.requireActiveMember(accountId);
        stocktakeService.refreshDraft(householdId, stocktakeId, clientVersion, locationId);
    }

    /**
     * 确认盘点。所有活跃成员可执行。
     */
    public StocktakeService.ConfirmResult confirmStocktake(UUID accountId, UUID householdId,
                                                           UUID stocktakeId, int clientVersion) {
        householdApi.requireActiveMember(accountId);
        return stocktakeService.confirm(householdId, stocktakeId, clientVersion, accountId);
    }

    /**
     * 取消盘点。所有活跃成员可执行。
     */
    public void cancelStocktake(UUID accountId, UUID householdId, UUID stocktakeId, int clientVersion) {
        householdApi.requireActiveMember(accountId);
        stocktakeService.cancel(householdId, stocktakeId, clientVersion);
    }

    /**
     * 查询盘点草稿行项（含物品名称、批次号、单位等展示信息）。所有活跃成员可执行。
     */
    public List<StocktakeItemWithDetails> stocktakeItems(UUID accountId, UUID householdId, UUID stocktakeId) {
        householdApi.requireActiveMember(accountId);
        return stocktakeService.draftItemsWithDetails(householdId, stocktakeId);
    }

    // ---- Admin-only commands ----

    /**
     * 冲正（撤销）一笔库存流水。仅管理员可执行。
     */
    public ReversalService.ReversalResult reverse(UUID accountId, UUID householdId,
                                                  UUID originalMovementId, String reason,
                                                  String memo, String idempotencyKey) {
        requireAdmin(accountId);
        return reversalService.reverse(householdId, accountId, originalMovementId,
                reason, memo, idempotencyKey);
    }

    /**
     * 一致性检查。仅管理员可执行。
     */
    public List<ConsistencyCheckService.Discrepancy> checkConsistency(UUID accountId,
                                                                      UUID householdId,
                                                                      UUID itemIdFilter) {
        requireAdmin(accountId);
        return consistencyCheckService.check(householdId, itemIdFilter);
    }

    // ---- Internal helpers ----

    /**
     * 要求当前账户为管理员，否则抛出 403。
     */
    private void requireAdmin(UUID accountId) {
        householdApi.requireActiveMember(accountId);
        if (!householdApi.hasAtLeastRole(accountId, HouseholdApi.MemberRole.ADMIN)) {
            throw new AccessDeniedException("需要管理员权限");
        }
    }
}
