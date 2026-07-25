package com.zija.inventory.internal;

import com.zija.inventory.internal.persistence.StockPositionEntity;
import com.zija.inventory.internal.persistence.StockPositionMapper;
import com.zija.inventory.internal.persistence.StocktakeEntity;
import com.zija.inventory.internal.persistence.StocktakeItemEntity;
import com.zija.inventory.internal.persistence.StocktakeItemMapper;
import com.zija.inventory.internal.persistence.StocktakeMapper;
import com.zija.location.LocationApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
class StocktakeService {

    private final StocktakeMapper stocktakeMapper;
    private final StocktakeItemMapper stocktakeItemMapper;
    private final StockPositionMapper stockPositionMapper;
    private final LotService lotService;
    private final LocationApi locationApi;

    StocktakeService(StocktakeMapper stocktakeMapper,
                     StocktakeItemMapper stocktakeItemMapper,
                     StockPositionMapper stockPositionMapper,
                     LotService lotService,
                     LocationApi locationApi) {
        this.stocktakeMapper = stocktakeMapper;
        this.stocktakeItemMapper = stocktakeItemMapper;
        this.stockPositionMapper = stockPositionMapper;
        this.lotService = lotService;
        this.locationApi = locationApi;
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
     * 盘点行项更新请求。
     */
    record StocktakeItemUpdate(UUID lotId, UUID locationId, BigDecimal actualQuantity, String reason) {}
}
