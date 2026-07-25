package com.zija.inventory.internal;

import com.zija.inventory.StockChangedEvent;
import com.zija.inventory.internal.event.InventoryEventPublisher;
import com.zija.inventory.internal.persistence.LotEntity;
import com.zija.inventory.internal.persistence.LotMapper;
import com.zija.inventory.internal.persistence.MovementEntity;
import com.zija.inventory.internal.persistence.MovementMapper;
import com.zija.inventory.internal.persistence.StockPositionEntity;
import com.zija.inventory.internal.persistence.StockPositionMapper;
import com.zija.inventory.internal.persistence.StocktakeEntity;
import com.zija.inventory.internal.persistence.StocktakeItemEntity;
import com.zija.inventory.internal.persistence.StocktakeItemMapper;
import com.zija.inventory.internal.persistence.StocktakeMapper;
import com.zija.location.LocationApi;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
class StocktakeService {

    private final StocktakeMapper stocktakeMapper;
    private final StocktakeItemMapper stocktakeItemMapper;
    private final StockPositionMapper stockPositionMapper;
    private final LotService lotService;
    private final LotMapper lotMapper;
    private final MovementMapper movementMapper;
    private final LocationApi locationApi;
    private final SystemApi systemApi;
    private final InventoryEventPublisher eventPublisher;

    StocktakeService(StocktakeMapper stocktakeMapper,
                     StocktakeItemMapper stocktakeItemMapper,
                     StockPositionMapper stockPositionMapper,
                     LotService lotService,
                     LotMapper lotMapper,
                     MovementMapper movementMapper,
                     LocationApi locationApi,
                     SystemApi systemApi,
                     InventoryEventPublisher eventPublisher) {
        this.stocktakeMapper = stocktakeMapper;
        this.stocktakeItemMapper = stocktakeItemMapper;
        this.stockPositionMapper = stockPositionMapper;
        this.lotService = lotService;
        this.lotMapper = lotMapper;
        this.movementMapper = movementMapper;
        this.locationApi = locationApi;
        this.systemApi = systemApi;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 创建盘点草稿：校验位置，插入盘点单，按位置快照当前库存位为盘点行项。
     *
     * @return 盘点单 id
     */
    @Transactional
    public UUID createDraft(UUID householdId, UUID accountId, UUID locationId) {
        // 1. 校验位置存在
        locationApi.requireLocation(householdId, locationId);

        // 2. 插入盘点单
        StocktakeEntity stocktake = new StocktakeEntity();
        stocktake.setId(UUID.randomUUID());
        stocktake.setHouseholdId(householdId);
        stocktake.setStatus("DRAFT");
        stocktake.setCreatedBy(accountId);
        stocktakeMapper.insert(stocktake);

        // 3. 查询该位置下所有库存位
        List<StockPositionEntity> positions = stockPositionMapper.findByLocation(householdId, locationId);

        // 4. 为每个库存位生成盘点行项
        if (!positions.isEmpty()) {
            List<StocktakeItemEntity> items = new ArrayList<>(positions.size());
            for (StockPositionEntity sp : positions) {
                StocktakeItemEntity item = new StocktakeItemEntity();
                item.setId(UUID.randomUUID());
                item.setStocktakeId(stocktake.getId());
                item.setHouseholdId(householdId);
                item.setLotId(sp.getLotId());
                item.setLocationId(locationId);
                item.setBookQuantity(sp.getQuantity());
                item.setActualQuantity(sp.getQuantity());
                item.setPositionRevision(sp.getRevision());
                items.add(item);
            }
            stocktakeItemMapper.batchInsert(items);
        }

        // 5. 返回盘点单 id
        return stocktake.getId();
    }

    /**
     * 刷新盘点草稿快照：删除所有行项，重新按位置快照当前库存位。
     *
     * @throws StocktakeNotDraftException 盘点单不是草稿状态
     * @throws InventoryLotVersionConflictException 盘点单版本冲突
     */
    @Transactional
    public void refreshDraft(UUID householdId, UUID stocktakeId, int clientVersion, UUID locationId) {
        // 1. 锁定盘点单，校验草稿状态
        StocktakeEntity stocktake = stocktakeMapper.lockById(householdId, stocktakeId);
        if (stocktake == null || !"DRAFT".equals(stocktake.getStatus())) {
            throw new StocktakeNotDraftException();
        }

        // 2. 乐观锁更新盘点单版本
        stocktake.setVersion(clientVersion);
        int rows = stocktakeMapper.updateById(stocktake);
        if (rows == 0) {
            throw new InventoryLotVersionConflictException();
        }

        // 3. 删除所有行项
        stocktakeItemMapper.deleteByStocktake(stocktakeId);

        // 4. 查询该位置下所有库存位
        List<StockPositionEntity> positions = stockPositionMapper.findByLocation(householdId, locationId);

        // 5. 为每个库存位生成盘点行项（与 createDraft 步骤 3-4 相同）
        if (!positions.isEmpty()) {
            List<StocktakeItemEntity> items = new ArrayList<>(positions.size());
            for (StockPositionEntity sp : positions) {
                StocktakeItemEntity item = new StocktakeItemEntity();
                item.setId(UUID.randomUUID());
                item.setStocktakeId(stocktakeId);
                item.setHouseholdId(householdId);
                item.setLotId(sp.getLotId());
                item.setLocationId(locationId);
                item.setBookQuantity(sp.getQuantity());
                item.setActualQuantity(sp.getQuantity());
                item.setPositionRevision(sp.getRevision());
                items.add(item);
            }
            stocktakeItemMapper.batchInsert(items);
        }
    }

    /**
     * 更新盘点草稿行项，支持补录账面为零的批次。
     *
     * @throws StocktakeNotDraftException          盘点单不是草稿状态，或补录时库存位数量 > 0 / 批次不存在
     * @throws InventoryLotVersionConflictException 盘点单版本冲突
     */
    @Transactional
    public void updateDraft(UUID householdId, UUID stocktakeId, int clientVersion,
                            List<StocktakeItemUpdate> updates) {
        // 1. 锁定盘点单，校验草稿状态
        StocktakeEntity stocktake = stocktakeMapper.lockById(householdId, stocktakeId);
        if (stocktake == null || !"DRAFT".equals(stocktake.getStatus())) {
            throw new StocktakeNotDraftException();
        }

        // 2. 乐观锁更新盘点单
        stocktake.setVersion(clientVersion);
        int rows = stocktakeMapper.updateById(stocktake);
        if (rows == 0) {
            throw new InventoryLotVersionConflictException();
        }

        // 3. 锁定现有行项
        List<StocktakeItemEntity> existingItems = stocktakeItemMapper.lockByStocktake(householdId, stocktakeId);

        // 4. 处理每条更新
        List<StocktakeItemEntity> backfillItems = new ArrayList<>();
        for (StocktakeItemUpdate update : updates) {
            // 4a. 查找匹配的现有行项
            StocktakeItemEntity matched = null;
            for (StocktakeItemEntity item : existingItems) {
                if (item.getLotId().equals(update.lotId()) && item.getLocationId().equals(update.locationId())) {
                    matched = item;
                    break;
                }
            }

            if (matched != null) {
                // 4b. 更新现有行项
                matched.setActualQuantity(update.actualQuantity());
                matched.setReason(update.reason());
                stocktakeItemMapper.updateById(matched);
            } else {
                // 4c. 补录：校验库存位和批次
                StockPositionEntity sp = stockPositionMapper.lockOne(householdId, update.lotId(), update.locationId());
                if (sp != null) {
                    if (sp.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                        throw new StocktakeNotDraftException();
                    }
                    // 库存位存在但数量为 0，允许补录
                } else {
                    // 库存位不存在，校验批次存在
                    try {
                        lotService.requireLot(householdId, update.lotId());
                    } catch (InventoryLotNotFoundException ex) {
                        throw new StocktakeNotDraftException();
                    }
                }

                // 4d. 插入补录行项
                StocktakeItemEntity newItem = new StocktakeItemEntity();
                newItem.setId(UUID.randomUUID());
                newItem.setStocktakeId(stocktakeId);
                newItem.setHouseholdId(householdId);
                newItem.setLotId(update.lotId());
                newItem.setLocationId(update.locationId());
                newItem.setBookQuantity(BigDecimal.ZERO);
                newItem.setActualQuantity(update.actualQuantity());
                newItem.setPositionRevision(0L);
                newItem.setReason(update.reason());
                backfillItems.add(newItem);
            }
        }

        // 5. 批量插入补录行项
        if (!backfillItems.isEmpty()) {
            stocktakeItemMapper.batchInsert(backfillItems);
        }
    }

    /**
     * 查询盘点草稿的所有行项。
     */
    @Transactional(readOnly = true)
    public List<StocktakeItemEntity> draftItems(UUID householdId, UUID stocktakeId) {
        return stocktakeItemMapper.findByStocktake(householdId, stocktakeId);
    }

    /**
     * 确认盘点：原子性校验库存未变，为差异生成 ADJUSTMENT 流水。
     *
     * @throws StocktakeNotDraftException              盘点单不是草稿状态
     * @throws InventoryLotVersionConflictException     盘点单版本冲突
     * @throws StocktakeStaleException                  盘点范围内库存已变化
     * @throws IllegalArgumentException                 差异行项缺少原因
     */
    @Transactional
    public ConfirmResult confirm(UUID householdId, UUID stocktakeId, int clientVersion, UUID accountId) {
        // 1. 锁定盘点单，校验草稿状态
        StocktakeEntity stocktake = stocktakeMapper.lockById(householdId, stocktakeId);
        if (stocktake == null || !"DRAFT".equals(stocktake.getStatus())) {
            throw new StocktakeNotDraftException();
        }

        // 2. 乐观锁版本检查
        stocktake.setVersion(clientVersion);
        int rows = stocktakeMapper.updateById(stocktake);
        if (rows == 0) {
            throw new InventoryLotVersionConflictException();
        }

        // 3. 锁定所有行项
        List<StocktakeItemEntity> items = stocktakeItemMapper.lockByStocktake(householdId, stocktakeId);

        // 4. 逐项校验库存位未变化
        for (StocktakeItemEntity item : items) {
            StockPositionEntity sp = stockPositionMapper.lockOne(
                    householdId, item.getLotId(), item.getLocationId());
            if (sp == null) {
                if (item.getBookQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    throw new StocktakeStaleException();
                }
            } else {
                if (sp.getQuantity().compareTo(item.getBookQuantity()) != 0
                        || !sp.getRevision().equals(item.getPositionRevision())) {
                    throw new StocktakeStaleException();
                }
            }
        }

        // 5. 处理差异：校验原因 → 生成 ADJUSTMENT 流水 → 更新库存位
        OffsetDateTime now = OffsetDateTime.now();
        int adjustedCount = 0;
        for (StocktakeItemEntity item : items) {
            int cmp = item.getActualQuantity().compareTo(item.getBookQuantity());
            if (cmp == 0) {
                continue;
            }

            // 差异必须有原因
            if (item.getReason() == null || item.getReason().isBlank()) {
                throw new IllegalArgumentException("盘点差异必须填写原因");
            }

            BigDecimal delta = item.getActualQuantity().subtract(item.getBookQuantity());
            UUID lotId = item.getLotId();
            UUID locId = item.getLocationId();

            // 更新库存位
            if (cmp > 0) {
                stockPositionMapper.addQuantity(householdId, lotId, locId, delta);
            } else {
                stockPositionMapper.subtractIfSufficient(
                        householdId, lotId, locId, delta.abs());
            }

            // 查询批次获取 itemId
            LotEntity lot = lotMapper.selectById(lotId);
            UUID itemId = lot.getItemId();

            // 生成 ADJUSTMENT 流水
            UUID movementId = UUID.randomUUID();
            var movement = new MovementEntity();
            movement.setId(movementId);
            movement.setHouseholdId(householdId);
            movement.setLotId(lotId);
            movement.setItemId(itemId);
            movement.setType("ADJUSTMENT");
            movement.setQuantity(delta.abs());
            movement.setFromLocationId(cmp < 0 ? locId : null);
            movement.setToLocationId(cmp > 0 ? locId : null);
            movement.setReason(item.getReason());
            movement.setMemo(null);
            movement.setOperatorAccountId(accountId);
            movement.setBusinessTime(now);
            movement.setCreatedAt(now);
            movement.setIdempotencyKey(UUID.randomUUID().toString());
            movement.setReversalOf(null);
            movementMapper.insert(movement);

            // 发布库存变更事件
            eventPublisher.publish(new StockChangedEvent(
                    UUID.randomUUID(), householdId, lotId, itemId,
                    "ADJUSTMENT", delta.abs(),
                    cmp < 0 ? locId : null, cmp > 0 ? locId : null,
                    now, movementId, UUID.fromString(movement.getIdempotencyKey())));

            adjustedCount++;
        }

        // 6. 盘点单状态 → COMPLETED
        stocktake.setStatus("COMPLETED");
        stocktake.setCompletedAt(now);
        stocktakeMapper.updateById(stocktake);

        // 7. 审计
        systemApi.recordAudit(new SystemApi.AuditEvent(
                "INVENTORY_STOCKTAKE_CONFIRM", "SUCCESS",
                householdId, accountId, null, null, null,
                Map.of("stocktakeId", stocktakeId, "adjustedCount", adjustedCount)));

        return new ConfirmResult(stocktakeId, adjustedCount);
    }

    /**
     * 盘点确认结果。
     */
    record ConfirmResult(UUID stocktakeId, int adjustedCount) {}

    /**
     * 盘点行项更新请求。
     */
    record StocktakeItemUpdate(UUID lotId, UUID locationId, BigDecimal actualQuantity, String reason) {}
}
