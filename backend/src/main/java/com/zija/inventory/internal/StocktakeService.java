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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
class StocktakeService {

    private final StocktakeMapper stocktakeMapper;
    private final StocktakeItemMapper stocktakeItemMapper;
    private final StockPositionMapper stockPositionMapper;
    private final LocationApi locationApi;

    StocktakeService(StocktakeMapper stocktakeMapper,
                     StocktakeItemMapper stocktakeItemMapper,
                     StockPositionMapper stockPositionMapper,
                     LocationApi locationApi) {
        this.stocktakeMapper = stocktakeMapper;
        this.stocktakeItemMapper = stocktakeItemMapper;
        this.stockPositionMapper = stockPositionMapper;
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
     * 查询盘点草稿的所有行项。
     */
    @Transactional(readOnly = true)
    public List<StocktakeItemEntity> draftItems(UUID householdId, UUID stocktakeId) {
        return stocktakeItemMapper.findByStocktake(householdId, stocktakeId);
    }
}
