package com.zija.reporting.internal.projection;

import com.zija.catalog.*;
import com.zija.household.HouseholdApi;
import com.zija.identity.IdentityApi;
import com.zija.inventory.InventoryApi;
import com.zija.inventory.StockChangedEvent;
import com.zija.location.LocationApi;
import com.zija.location.LocationChangedEvent;
import com.zija.reporting.internal.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 投影事件监听器。使用 AFTER_COMMIT 阶段确保发布事务提交后再读取数据，
 * 去重与 upsert 在同一 REQUIRES_NEW 事务中完成。
 * 失败时写 dead-letter 并允许重试。
 */
@Service
public class ProjectionListener {

    private static final Logger log = LoggerFactory.getLogger(ProjectionListener.class);

    private final ReportingProcessedEventMapper processedEventMapper;
    private final ReportingDeadLetterMapper deadLetterMapper;
    private final SearchIndexMapper searchIndexMapper;
    private final StockFlatMapper stockFlatMapper;
    private final MovementFlatMapper movementFlatMapper;
    private final CatalogApi catalogApi;
    private final LocationApi locationApi;
    private final InventoryApi inventoryApi;
    private final IdentityApi identityApi;
    private final HouseholdApi householdApi;
    private final TransactionTemplate requiresNewTx;

    public ProjectionListener(ReportingProcessedEventMapper processedEventMapper,
                               ReportingDeadLetterMapper deadLetterMapper,
                               SearchIndexMapper searchIndexMapper,
                               StockFlatMapper stockFlatMapper,
                               MovementFlatMapper movementFlatMapper,
                               CatalogApi catalogApi,
                               LocationApi locationApi,
                               InventoryApi inventoryApi,
                               IdentityApi identityApi,
                               HouseholdApi householdApi,
                               PlatformTransactionManager txManager) {
        this.processedEventMapper = processedEventMapper;
        this.deadLetterMapper = deadLetterMapper;
        this.searchIndexMapper = searchIndexMapper;
        this.stockFlatMapper = stockFlatMapper;
        this.movementFlatMapper = movementFlatMapper;
        this.catalogApi = catalogApi;
        this.locationApi = locationApi;
        this.inventoryApi = inventoryApi;
        this.identityApi = identityApi;
        this.householdApi = householdApi;
        this.requiresNewTx = new TransactionTemplate(txManager);
        this.requiresNewTx.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
    }

    // ===== 库存事件 =====

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockChanged(StockChangedEvent evt) {
        try {
            requiresNewTx.executeWithoutResult(status -> {
                int rows = processedEventMapper.insertOnConflictDoNothing(
                        evt.eventId(), "StockChangedEvent");
                if (rows == 0) return;
                handleStockChanged(evt);
            });
        } catch (RuntimeException ex) {
            saveDeadLetterInNewTx(evt.eventId(), "StockChangedEvent", toMap(evt), ex);
            log.warn("StockChangedEvent projection failed, wrote dead-letter: eventId={}",
                    evt.eventId(), ex);
        }
    }

    private void handleStockChanged(StockChangedEvent evt) {
        // 1. upsert reporting_movement_flat
        var movEntity = buildMovementFlat(evt);
        movementFlatMapper.upsert(movEntity);

        // 2. upsert reporting_stock_flat（数量变更 → 重新拉取该物品所有库存位，按 lotId 筛选）
        rebuildStockFlatForLot(evt.householdId(), evt.itemId(), evt.lotId());
    }

    private MovementFlatEntity buildMovementFlat(StockChangedEvent evt) {
        var e = new MovementFlatEntity();
        e.setHouseholdId(evt.householdId());
        e.setMovementId(evt.movementId());
        e.setEventId(evt.eventId());
        e.setLotId(evt.lotId());
        e.setItemId(evt.itemId());
        e.setItemName(resolveItemName(evt.householdId(), evt.itemId()));
        e.setType(evt.movementType());
        e.setQuantityDelta(evt.quantityDelta());
        e.setFromLocationId(evt.fromLocationId());
        e.setToLocationId(evt.toLocationId());
        e.setFromLocationPath(resolveLocationPath(evt.householdId(), evt.fromLocationId()));
        e.setToLocationPath(resolveLocationPath(evt.householdId(), evt.toLocationId()));
        e.setOperatorAccountId(evt.operatorAccountId());
        e.setOperatorDisplayName(resolveDisplayName(evt.operatorAccountId()));
        e.setReason(evt.reason());
        e.setReversalOf(evt.reversalOf());
        e.setBusinessTime(evt.businessTime());
        e.setCreatedAt(OffsetDateTime.now());
        return e;
    }

    private void rebuildStockFlatForLot(UUID householdId, UUID itemId, UUID lotId) {
        stockFlatMapper.deleteByLot(householdId, lotId);
        var item = resolveItemInfo(householdId, itemId);
        var positions = inventoryApi.stockPositionsOfItem(householdId, itemId);
        for (var pos : positions) {
            if (!pos.lotId().equals(lotId)) continue;
            var e = new StockFlatEntity();
            e.setHouseholdId(householdId);
            e.setLotId(pos.lotId());
            e.setItemId(itemId);
            e.setItemName(item != null ? item.name() : itemId.toString());
            e.setUnitName(item != null && item.unitId() != null
                    ? resolveUnitName(householdId, item.unitId()) : null);
            e.setLocationId(pos.locationId());
            e.setLocationPath(resolveLocationPath(householdId, pos.locationId()));
            e.setQuantity(pos.quantity());
            e.setUpdatedAt(OffsetDateTime.now());
            stockFlatMapper.upsert(e);
        }
    }

    // ===== Catalog 事件 =====

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onItemChanged(ItemChangedEvent evt) {
        try {
            requiresNewTx.executeWithoutResult(status -> {
                int rows = processedEventMapper.insertOnConflictDoNothing(
                        evt.eventId(), "ItemChangedEvent");
                if (rows == 0) return;
                handleItemChanged(evt);
            });
        } catch (RuntimeException ex) {
            saveDeadLetterInNewTx(evt.eventId(), "ItemChangedEvent", toMap(evt), ex);
            log.warn("ItemChangedEvent projection failed: eventId={}", evt.eventId(), ex);
        }
    }

    private void handleItemChanged(ItemChangedEvent evt) {
        if ("ARCHIVED".equals(evt.changeType())) {
            searchIndexMapper.deleteByEntity(evt.householdId(), "ITEM", evt.itemId());
            return;
        }
        // 从 CatalogApi.dumpItems 拉取最新数据重建搜索索引（分页遍历直到找到目标物品）
        OffsetDateTime cursor = OffsetDateTime.MIN;
        boolean hasMore = true;
        while (hasMore) {
            var page = catalogApi.dumpItems(evt.householdId(), cursor, 1000);
            for (var item : page.items()) {
                if (item.itemId().equals(evt.itemId())) {
                    searchIndexMapper.upsert(buildItemSearchIndex(evt.householdId(), item));
                    return;
                }
            }
            cursor = page.nextCursor();
            hasMore = page.hasMore();
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCategoryChanged(CategoryChangedEvent evt) {
        try {
            requiresNewTx.executeWithoutResult(status -> {
                int rows = processedEventMapper.insertOnConflictDoNothing(
                        evt.eventId(), "CategoryChangedEvent");
                if (rows == 0) return;
                // 分类变更 → 重建受影响物品的 search_index 行
                OffsetDateTime cursor = OffsetDateTime.MIN;
                boolean hasMore = true;
                while (hasMore) {
                    var page = catalogApi.dumpItems(evt.householdId(), cursor, 1000);
                    for (var item : page.items()) {
                        if (evt.categoryId().equals(item.categoryId())) {
                            searchIndexMapper.upsert(buildItemSearchIndex(evt.householdId(), item));
                        }
                    }
                    cursor = page.nextCursor();
                    hasMore = page.hasMore();
                }
            });
        } catch (RuntimeException ex) {
            saveDeadLetterInNewTx(evt.eventId(), "CategoryChangedEvent", toMap(evt), ex);
            log.warn("CategoryChangedEvent projection failed: eventId={}", evt.eventId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBrandChanged(BrandChangedEvent evt) {
        try {
            requiresNewTx.executeWithoutResult(status -> {
                int rows = processedEventMapper.insertOnConflictDoNothing(
                        evt.eventId(), "BrandChangedEvent");
                if (rows == 0) return;
                // 品牌变更 → 重建受影响物品的 search_index 行
                OffsetDateTime cursor = OffsetDateTime.MIN;
                boolean hasMore = true;
                while (hasMore) {
                    var page = catalogApi.dumpItems(evt.householdId(), cursor, 1000);
                    for (var item : page.items()) {
                        if (evt.brandId().equals(item.brandId())) {
                            searchIndexMapper.upsert(buildItemSearchIndex(evt.householdId(), item));
                        }
                    }
                    cursor = page.nextCursor();
                    hasMore = page.hasMore();
                }
            });
        } catch (RuntimeException ex) {
            saveDeadLetterInNewTx(evt.eventId(), "BrandChangedEvent", toMap(evt), ex);
            log.warn("BrandChangedEvent projection failed: eventId={}", evt.eventId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUnitChanged(UnitChangedEvent evt) {
        try {
            requiresNewTx.executeWithoutResult(status -> {
                int rows = processedEventMapper.insertOnConflictDoNothing(
                        evt.eventId(), "UnitChangedEvent");
                if (rows == 0) return;
                // 单位变更 → 重建受影响物品的 search_index 行
                OffsetDateTime cursor = OffsetDateTime.MIN;
                boolean hasMore = true;
                while (hasMore) {
                    var page = catalogApi.dumpItems(evt.householdId(), cursor, 1000);
                    for (var item : page.items()) {
                        if (evt.unitId().equals(item.unitId())) {
                            searchIndexMapper.upsert(buildItemSearchIndex(evt.householdId(), item));
                        }
                    }
                    cursor = page.nextCursor();
                    hasMore = page.hasMore();
                }
            });
        } catch (RuntimeException ex) {
            saveDeadLetterInNewTx(evt.eventId(), "UnitChangedEvent", toMap(evt), ex);
            log.warn("UnitChangedEvent projection failed: eventId={}", evt.eventId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTagChanged(TagChangedEvent evt) {
        try {
            requiresNewTx.executeWithoutResult(status -> {
                int rows = processedEventMapper.insertOnConflictDoNothing(
                        evt.eventId(), "TagChangedEvent");
                if (rows == 0) return;
                // 标签变更 → 重建受影响物品的 search_index 行（tag_names 字段）
                // 由于 TagChangedEvent 不含物品列表，重建该家庭所有物品的搜索索引
                OffsetDateTime cursor = OffsetDateTime.MIN;
                boolean hasMore = true;
                while (hasMore) {
                    var page = catalogApi.dumpItems(evt.householdId(), cursor, 1000);
                    for (var item : page.items()) {
                        searchIndexMapper.upsert(buildItemSearchIndex(evt.householdId(), item));
                    }
                    cursor = page.nextCursor();
                    hasMore = page.hasMore();
                }
            });
        } catch (RuntimeException ex) {
            saveDeadLetterInNewTx(evt.eventId(), "TagChangedEvent", toMap(evt), ex);
            log.warn("TagChangedEvent projection failed: eventId={}", evt.eventId(), ex);
        }
    }

    // ===== Location 事件 =====

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLocationChanged(LocationChangedEvent evt) {
        try {
            requiresNewTx.executeWithoutResult(status -> {
                int rows = processedEventMapper.insertOnConflictDoNothing(
                        evt.eventId(), "LocationChangedEvent");
                if (rows == 0) return;
                handleLocationChanged(evt);
            });
        } catch (RuntimeException ex) {
            saveDeadLetterInNewTx(evt.eventId(), "LocationChangedEvent", toMap(evt), ex);
            log.warn("LocationChangedEvent projection failed: eventId={}", evt.eventId(), ex);
        }
    }

    private void handleLocationChanged(LocationChangedEvent evt) {
        if ("DELETED".equals(evt.changeType())) {
            searchIndexMapper.deleteByEntity(evt.householdId(), "LOCATION", evt.locationId());
            return;
        }
        // 从 LocationApi.dumpTree 拉取最新数据重建搜索索引
        OffsetDateTime cursor = OffsetDateTime.MIN;
        boolean hasMore = true;
        while (hasMore) {
            var page = locationApi.dumpTree(evt.householdId(), cursor, 1000);
            for (var loc : page.items()) {
                if (loc.locationId().equals(evt.locationId())) {
                    searchIndexMapper.upsert(buildLocationSearchIndex(evt.householdId(), loc));
                    break;
                }
            }
            cursor = page.nextCursor();
            hasMore = page.hasMore();
        }
    }

    // ===== SearchIndex 构建 =====

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

    // ===== 辅助方法 =====

    private void saveDeadLetterInNewTx(UUID eventId, String eventType,
                                        Map<String, Object> payload, Throwable err) {
        requiresNewTx.executeWithoutResult(status -> {
            processedEventMapper.deleteById(eventId);
            var dl = new DeadLetterEntity();
            dl.setId(UUID.randomUUID());
            dl.setEventId(eventId);
            dl.setEventType(eventType);
            dl.setPayload(payload);
            dl.setFailureCount(1);
            dl.setNextRetryAt(OffsetDateTime.now().plusSeconds(30));
            dl.setLastError(truncate(err.getMessage(), 4000));
            dl.setAbandoned(false);
            dl.setCreatedAt(OffsetDateTime.now());
            try {
                deadLetterMapper.insert(dl);
            } catch (org.springframework.dao.DuplicateKeyException ignored) {
                // 并发写入，忽略
            }
        });
    }

    private String resolveItemName(UUID householdId, UUID itemId) {
        try {
            var item = catalogApi.requireItem(householdId, itemId);
            return item.name();
        } catch (Exception e) {
            return itemId.toString();
        }
    }

    private CatalogApi.ItemInfo resolveItemInfo(UUID householdId, UUID itemId) {
        try {
            return catalogApi.requireItem(householdId, itemId);
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveUnitName(UUID householdId, UUID unitId) {
        if (unitId == null) return null;
        try {
            var unit = catalogApi.requireUnit(householdId, unitId);
            return unit.name();
        } catch (Exception e) {
            return unitId.toString();
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

    private static String truncate(String s, int max) {
        if (s == null) return "UnknownError";
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ===== toMap 方法：将各类事件转为 Map 用于 dead-letter payload =====

    private Map<String, Object> toMap(StockChangedEvent evt) {
        return Map.ofEntries(
                Map.entry("eventId", evt.eventId().toString()),
                Map.entry("householdId", evt.householdId().toString()),
                Map.entry("lotId", evt.lotId().toString()),
                Map.entry("itemId", evt.itemId().toString()),
                Map.entry("movementType", evt.movementType()),
                Map.entry("quantityDelta", evt.quantityDelta().toString()),
                Map.entry("fromLocationId", evt.fromLocationId() == null ? "" : evt.fromLocationId().toString()),
                Map.entry("toLocationId", evt.toLocationId() == null ? "" : evt.toLocationId().toString()),
                Map.entry("businessTime", evt.businessTime().toString()),
                Map.entry("movementId", evt.movementId().toString()),
                Map.entry("idempotencyKey", evt.idempotencyKey().toString()),
                Map.entry("operatorAccountId", evt.operatorAccountId() == null ? "" : evt.operatorAccountId().toString()),
                Map.entry("reason", evt.reason() == null ? "" : evt.reason()),
                Map.entry("reversalOf", evt.reversalOf() == null ? "" : evt.reversalOf().toString())
        );
    }

    private Map<String, Object> toMap(ItemChangedEvent evt) {
        return Map.of(
                "eventId", evt.eventId().toString(),
                "householdId", evt.householdId().toString(),
                "itemId", evt.itemId().toString(),
                "changeType", evt.changeType()
        );
    }

    private Map<String, Object> toMap(CategoryChangedEvent evt) {
        return Map.of(
                "eventId", evt.eventId().toString(),
                "householdId", evt.householdId().toString(),
                "categoryId", evt.categoryId().toString(),
                "changeType", evt.changeType()
        );
    }

    private Map<String, Object> toMap(BrandChangedEvent evt) {
        return Map.of(
                "eventId", evt.eventId().toString(),
                "householdId", evt.householdId().toString(),
                "brandId", evt.brandId().toString(),
                "changeType", evt.changeType()
        );
    }

    private Map<String, Object> toMap(UnitChangedEvent evt) {
        return Map.of(
                "eventId", evt.eventId().toString(),
                "householdId", evt.householdId().toString(),
                "unitId", evt.unitId().toString(),
                "changeType", evt.changeType()
        );
    }

    private Map<String, Object> toMap(TagChangedEvent evt) {
        return Map.of(
                "eventId", evt.eventId().toString(),
                "householdId", evt.householdId().toString(),
                "tagId", evt.tagId().toString(),
                "changeType", evt.changeType()
        );
    }

    private Map<String, Object> toMap(LocationChangedEvent evt) {
        return Map.ofEntries(
                Map.entry("eventId", evt.eventId().toString()),
                Map.entry("householdId", evt.householdId().toString()),
                Map.entry("locationId", evt.locationId().toString()),
                Map.entry("changeType", evt.changeType()),
                Map.entry("parentId", evt.parentId() == null ? "" : evt.parentId().toString())
        );
    }
}
