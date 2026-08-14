package com.zija.reporting.internal.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zija.shared.ZijaAuditOutcome;
import com.zija.catalog.CatalogApi;
import com.zija.inventory.InventoryApi;
import com.zija.location.LocationApi;
import com.zija.reporting.internal.persistence.*;
import com.zija.system.SystemApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 投影重建器。清空指定家庭投影行，从源模块快照拉取端口回填。
 * 写 REPORTING_PROJECTION_REBUILT 审计。
 */
@Service
public class ProjectionRebuilder {

    private static final Logger log = LoggerFactory.getLogger(ProjectionRebuilder.class);
    private static final int BATCH_SIZE = 1000;

    private final SearchIndexMapper searchIndexMapper;
    private final StockFlatMapper stockFlatMapper;
    private final MovementFlatMapper movementFlatMapper;
    private final CatalogApi catalogApi;
    private final LocationApi locationApi;
    private final InventoryApi inventoryApi;
    private final SystemApi systemApi;
    private final ProjectionEntityBuilder builder;

    public ProjectionRebuilder(SearchIndexMapper searchIndexMapper,
                                StockFlatMapper stockFlatMapper,
                                MovementFlatMapper movementFlatMapper,
                                CatalogApi catalogApi,
                                LocationApi locationApi,
                                InventoryApi inventoryApi,
                                SystemApi systemApi,
                                ProjectionEntityBuilder builder) {
        this.searchIndexMapper = searchIndexMapper;
        this.stockFlatMapper = stockFlatMapper;
        this.movementFlatMapper = movementFlatMapper;
        this.catalogApi = catalogApi;
        this.locationApi = locationApi;
        this.inventoryApi = inventoryApi;
        this.systemApi = systemApi;
        this.builder = builder;
    }

    @Transactional
    public void rebuild(UUID householdId) {
        log.info("Starting projection rebuild for household: {}", householdId);

        // 1. 清空该家庭所有投影行
        clearProjections(householdId);

        // 2. 从快照拉取端口回填
        rebuildSearchIndex(householdId);
        rebuildStockFlat(householdId);
        rebuildMovementFlat(householdId);

        // 3. 写审计
        systemApi.recordAudit(new SystemApi.AuditEvent(
                SystemApi.AuditAction.REPORTING_PROJECTION_REBUILT, ZijaAuditOutcome.SUCCESS, householdId, null, null, null, null,
                Map.of("householdId", householdId.toString())));

        log.info("Projection rebuild complete for household: {}", householdId);
    }

    private void clearProjections(UUID householdId) {
        searchIndexMapper.delete(new LambdaQueryWrapper<SearchIndexEntity>()
                .eq(SearchIndexEntity::getHouseholdId, householdId));
        stockFlatMapper.delete(new LambdaQueryWrapper<StockFlatEntity>()
                .eq(StockFlatEntity::getHouseholdId, householdId));
        movementFlatMapper.delete(new LambdaQueryWrapper<MovementFlatEntity>()
                .eq(MovementFlatEntity::getHouseholdId, householdId));
    }

    private void rebuildSearchIndex(UUID householdId) {
        // 重建物品搜索索引
        OffsetDateTime cursor = OffsetDateTime.MIN;
        boolean hasMore = true;
        while (hasMore) {
            var page = catalogApi.dumpItems(householdId, cursor, BATCH_SIZE);
            for (var item : page.items()) {
                searchIndexMapper.upsert(builder.buildItemSearchIndex(householdId, item));
            }
            hasMore = page.hasMore();
            cursor = page.nextCursor();
        }

        // 重建位置搜索索引
        cursor = OffsetDateTime.MIN;
        hasMore = true;
        while (hasMore) {
            var page = locationApi.dumpTree(householdId, cursor, BATCH_SIZE);
            for (var loc : page.items()) {
                searchIndexMapper.upsert(builder.buildLocationSearchIndex(householdId, loc));
            }
            hasMore = page.hasMore();
            cursor = page.nextCursor();
        }
    }

    private void rebuildStockFlat(UUID householdId) {
        Map<UUID, InventoryApi.LotFlat> lotCache = new HashMap<>();
        OffsetDateTime cursor = OffsetDateTime.MIN;
        boolean hasMore = true;
        while (hasMore) {
            var page = inventoryApi.dumpStockPositions(householdId, cursor, BATCH_SIZE);
            for (var pos : page.items()) {
                stockFlatMapper.upsert(buildStockFlat(householdId, pos, lotCache));
            }
            hasMore = page.hasMore();
            cursor = page.nextCursor();
        }
    }

    private void rebuildMovementFlat(UUID householdId) {
        OffsetDateTime cursor = OffsetDateTime.MIN;
        boolean hasMore = true;
        while (hasMore) {
            var page = inventoryApi.dumpMovements(householdId, cursor, BATCH_SIZE);
            for (var mov : page.items()) {
                movementFlatMapper.upsert(buildMovementFlat(householdId, mov));
            }
            hasMore = page.hasMore();
            cursor = page.nextCursor();
        }
    }

    // ===== 构建方法 =====

    private StockFlatEntity buildStockFlat(UUID householdId, InventoryApi.StockPositionDump pos,
                                            Map<UUID, InventoryApi.LotFlat> lotCache) {
        var lot = lotCache.computeIfAbsent(pos.lotId(),
                l -> inventoryApi.findLot(householdId, l).orElse(null));
        return builder.buildStockFlat(householdId, pos.lotId(), pos.itemId(),
                builder.resolveItemName(householdId, pos.itemId()),
                builder.resolveItemUnitName(householdId, pos.itemId()),
                lot, pos.locationId(), pos.quantity());
    }

    private MovementFlatEntity buildMovementFlat(UUID householdId, InventoryApi.MovementDump mov) {
        return builder.buildMovementFlat(
                householdId, mov.id(), mov.id(), // rebuild: use movement ID as synthetic event ID
                mov.lotId(), mov.itemId(), mov.type(),
                mov.quantityDelta(), mov.fromLocationId(), mov.toLocationId(),
                mov.operatorAccountId(), mov.reason(), mov.reversalOf(), mov.businessTime());
    }
}
