package com.zija.reporting.internal.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zija.catalog.CatalogApi;
import com.zija.identity.IdentityApi;
import com.zija.inventory.InventoryApi;
import com.zija.location.LocationApi;
import com.zija.reporting.internal.persistence.*;
import com.zija.system.SystemApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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
    private final IdentityApi identityApi;
    private final SystemApi systemApi;

    public ProjectionRebuilder(SearchIndexMapper searchIndexMapper,
                                StockFlatMapper stockFlatMapper,
                                MovementFlatMapper movementFlatMapper,
                                CatalogApi catalogApi,
                                LocationApi locationApi,
                                InventoryApi inventoryApi,
                                IdentityApi identityApi,
                                SystemApi systemApi) {
        this.searchIndexMapper = searchIndexMapper;
        this.stockFlatMapper = stockFlatMapper;
        this.movementFlatMapper = movementFlatMapper;
        this.catalogApi = catalogApi;
        this.locationApi = locationApi;
        this.inventoryApi = inventoryApi;
        this.identityApi = identityApi;
        this.systemApi = systemApi;
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
                "REPORTING_PROJECTION_REBUILT", "SUCCESS", householdId, null, null, null, null,
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
                searchIndexMapper.upsert(buildItemSearchIndex(householdId, item));
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
                searchIndexMapper.upsert(buildLocationSearchIndex(householdId, loc));
            }
            hasMore = page.hasMore();
            cursor = page.nextCursor();
        }
    }

    private void rebuildStockFlat(UUID householdId) {
        OffsetDateTime cursor = OffsetDateTime.MIN;
        boolean hasMore = true;
        while (hasMore) {
            var page = inventoryApi.dumpStockPositions(householdId, cursor, BATCH_SIZE);
            for (var pos : page.items()) {
                stockFlatMapper.upsert(buildStockFlat(householdId, pos));
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

    private SearchIndexEntity buildItemSearchIndex(UUID householdId, CatalogApi.ItemFlat item) {
        var e = new SearchIndexEntity();
        e.setHouseholdId(householdId);
        e.setEntityType("ITEM");
        e.setEntityId(item.itemId());
        e.setItemName(item.name());
        e.setBrandName(item.brandName());
        e.setTagNames(item.tagNames());
        e.setCategoryName(item.categoryName());
        e.setUnitName(item.unitName());
        e.setUpdatedAt(OffsetDateTime.now());
        return e;
    }

    private SearchIndexEntity buildLocationSearchIndex(UUID householdId, LocationApi.LocationFlat loc) {
        var e = new SearchIndexEntity();
        e.setHouseholdId(householdId);
        e.setEntityType("LOCATION");
        e.setEntityId(loc.locationId());
        e.setLocationName(loc.name());
        e.setLocationPath(loc.path());
        e.setUpdatedAt(OffsetDateTime.now());
        return e;
    }

    private StockFlatEntity buildStockFlat(UUID householdId, InventoryApi.StockPositionDump pos) {
        var e = new StockFlatEntity();
        e.setHouseholdId(householdId);
        e.setLotId(pos.lotId());
        e.setItemId(pos.itemId());
        e.setItemName(resolveItemName(householdId, pos.itemId()));
        e.setLocationId(pos.locationId());
        e.setLocationPath(resolveLocationPath(householdId, pos.locationId()));
        e.setQuantity(pos.quantity());
        e.setUpdatedAt(OffsetDateTime.now());
        return e;
    }

    private MovementFlatEntity buildMovementFlat(UUID householdId, InventoryApi.MovementDump mov) {
        var e = new MovementFlatEntity();
        e.setHouseholdId(householdId);
        e.setMovementId(mov.id());
        e.setLotId(mov.lotId());
        e.setItemId(mov.itemId());
        e.setItemName(resolveItemName(householdId, mov.itemId()));
        e.setType(mov.type());
        e.setQuantityDelta(mov.quantityDelta());
        e.setFromLocationId(mov.fromLocationId());
        e.setToLocationId(mov.toLocationId());
        e.setFromLocationPath(resolveLocationPath(householdId, mov.fromLocationId()));
        e.setToLocationPath(resolveLocationPath(householdId, mov.toLocationId()));
        e.setOperatorAccountId(mov.operatorAccountId());
        e.setOperatorDisplayName(resolveDisplayName(mov.operatorAccountId()));
        e.setReason(mov.reason());
        e.setReversalOf(mov.reversalOf());
        e.setBusinessTime(mov.businessTime());
        e.setCreatedAt(OffsetDateTime.now());
        return e;
    }

    // ===== 名称解析辅助方法 =====

    private String resolveItemName(UUID householdId, UUID itemId) {
        try {
            var item = catalogApi.requireItem(householdId, itemId);
            return item.name();
        } catch (Exception e) {
            return itemId.toString();
        }
    }

    private String resolveLocationPath(UUID householdId, UUID locationId) {
        if (locationId == null) return null;
        try {
            var loc = locationApi.requireLocation(householdId, locationId);
            return loc.name();
        } catch (Exception e) {
            return locationId.toString();
        }
    }

    private String resolveDisplayName(UUID accountId) {
        if (accountId == null) return null;
        try {
            var account = identityApi.findById(accountId);
            return account.map(IdentityApi.AccountInfo::displayName).orElse(accountId.toString());
        } catch (Exception e) {
            return accountId.toString();
        }
    }
}
