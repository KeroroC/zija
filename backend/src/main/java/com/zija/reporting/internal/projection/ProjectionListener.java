package com.zija.reporting.internal.projection;

import com.zija.catalog.*;
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
    private final ProjectionEntityBuilder builder;
    private final TransactionTemplate requiresNewTx;

    public ProjectionListener(ReportingProcessedEventMapper processedEventMapper,
                               ReportingDeadLetterMapper deadLetterMapper,
                               SearchIndexMapper searchIndexMapper,
                               StockFlatMapper stockFlatMapper,
                               MovementFlatMapper movementFlatMapper,
                               CatalogApi catalogApi,
                               LocationApi locationApi,
                               InventoryApi inventoryApi,
                               ProjectionEntityBuilder builder,
                               PlatformTransactionManager txManager) {
        this.processedEventMapper = processedEventMapper;
        this.deadLetterMapper = deadLetterMapper;
        this.searchIndexMapper = searchIndexMapper;
        this.stockFlatMapper = stockFlatMapper;
        this.movementFlatMapper = movementFlatMapper;
        this.catalogApi = catalogApi;
        this.locationApi = locationApi;
        this.inventoryApi = inventoryApi;
        this.builder = builder;
        this.requiresNewTx = new TransactionTemplate(txManager);
        this.requiresNewTx.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
    }

    // ===== 库存事件 =====

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockChanged(StockChangedEvent evt) {
        try {
            processStockChangedEvent(evt);
        } catch (RuntimeException ex) {
            saveDeadLetterInNewTx(evt.eventId(), "StockChangedEvent", toMap(evt), ex);
            log.warn("StockChangedEvent projection failed, wrote dead-letter: eventId={}",
                    evt.eventId(), ex);
        }
    }

    /**
     * 实际处理 StockChangedEvent 投影：去重 + 重建 movement_flat / stock_flat。
     * 失败时向上抛异常，由调用方决定写 dead-letter 或走重投逻辑。
     * 供 {@link ReportingEventRetryService} 重投时调用——必须用此方法而非
     * {@link #onStockChanged}，否则监听器的 catch 会吞掉异常，重投服务无法触发
     * incrementFailure / markAbandoned。
     */
    public void processStockChangedEvent(StockChangedEvent evt) {
        requiresNewTx.executeWithoutResult(status -> {
            int rows = processedEventMapper.insertOnConflictDoNothing(
                    evt.eventId(), "StockChangedEvent");
            if (rows == 0) return;
            handleStockChanged(evt);
        });
    }

    private void handleStockChanged(StockChangedEvent evt) {
        // 1. upsert reporting_movement_flat
        var movEntity = buildMovementFlat(evt);
        movementFlatMapper.upsert(movEntity);

        // 2. upsert reporting_stock_flat（数量变更 → 重新拉取该物品所有库存位，按 lotId 筛选）
        rebuildStockFlatForLot(evt.householdId(), evt.itemId(), evt.lotId());
    }

    private MovementFlatEntity buildMovementFlat(StockChangedEvent evt) {
        return builder.buildMovementFlat(
                evt.householdId(), evt.movementId(), evt.eventId(),
                evt.lotId(), evt.itemId(), evt.movementType(),
                evt.quantityDelta(), evt.fromLocationId(), evt.toLocationId(),
                evt.operatorAccountId(), evt.reason(), evt.reversalOf(), evt.businessTime());
    }

    private void rebuildStockFlatForLot(UUID householdId, UUID itemId, UUID lotId) {
        stockFlatMapper.deleteByLot(householdId, lotId);
        var item = resolveItemInfo(householdId, itemId);
        var lot = inventoryApi.findLot(householdId, lotId).orElse(null);
        var positions = inventoryApi.stockPositionsOfItem(householdId, itemId);
        for (var pos : positions) {
            if (!pos.lotId().equals(lotId)) continue;
            String itemName = item != null ? item.name() : itemId.toString();
            String unitName = item != null && item.unitId() != null
                    ? builder.resolveUnitName(householdId, item.unitId()) : null;
            stockFlatMapper.upsert(builder.buildStockFlat(
                    householdId, pos.lotId(), itemId, itemName, unitName,
                    lot, pos.locationId(), pos.quantity()));
        }
    }

    // ===== Catalog 事件 =====

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onItemChanged(ItemChangedEvent evt) {
        try {
            processItemChangedEvent(evt);
        } catch (RuntimeException ex) {
            saveDeadLetterInNewTx(evt.eventId(), "ItemChangedEvent", toMap(evt), ex);
            log.warn("ItemChangedEvent projection failed: eventId={}", evt.eventId(), ex);
        }
    }

    /** 实际处理 ItemChangedEvent 投影：重建 search_index。失败抛。 */
    public void processItemChangedEvent(ItemChangedEvent evt) {
        requiresNewTx.executeWithoutResult(status -> {
            int rows = processedEventMapper.insertOnConflictDoNothing(
                    evt.eventId(), "ItemChangedEvent");
            if (rows == 0) return;
            handleItemChanged(evt);
        });
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
                    searchIndexMapper.upsert(builder.buildItemSearchIndex(evt.householdId(), item));
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
            processCategoryChangedEvent(evt);
        } catch (RuntimeException ex) {
            saveDeadLetterInNewTx(evt.eventId(), "CategoryChangedEvent", toMap(evt), ex);
            log.warn("CategoryChangedEvent projection failed: eventId={}", evt.eventId(), ex);
        }
    }

    /** 实际处理 CategoryChangedEvent 投影。失败抛。 */
    public void processCategoryChangedEvent(CategoryChangedEvent evt) {
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
                        searchIndexMapper.upsert(builder.buildItemSearchIndex(evt.householdId(), item));
                    }
                }
                cursor = page.nextCursor();
                hasMore = page.hasMore();
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBrandChanged(BrandChangedEvent evt) {
        try {
            processBrandChangedEvent(evt);
        } catch (RuntimeException ex) {
            saveDeadLetterInNewTx(evt.eventId(), "BrandChangedEvent", toMap(evt), ex);
            log.warn("BrandChangedEvent projection failed: eventId={}", evt.eventId(), ex);
        }
    }

    /** 实际处理 BrandChangedEvent 投影。失败抛。 */
    public void processBrandChangedEvent(BrandChangedEvent evt) {
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
                        searchIndexMapper.upsert(builder.buildItemSearchIndex(evt.householdId(), item));
                    }
                }
                cursor = page.nextCursor();
                hasMore = page.hasMore();
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUnitChanged(UnitChangedEvent evt) {
        try {
            processUnitChangedEvent(evt);
        } catch (RuntimeException ex) {
            saveDeadLetterInNewTx(evt.eventId(), "UnitChangedEvent", toMap(evt), ex);
            log.warn("UnitChangedEvent projection failed: eventId={}", evt.eventId(), ex);
        }
    }

    /** 实际处理 UnitChangedEvent 投影。失败抛。 */
    public void processUnitChangedEvent(UnitChangedEvent evt) {
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
                        searchIndexMapper.upsert(builder.buildItemSearchIndex(evt.householdId(), item));
                    }
                }
                cursor = page.nextCursor();
                hasMore = page.hasMore();
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTagChanged(TagChangedEvent evt) {
        try {
            processTagChangedEvent(evt);
        } catch (RuntimeException ex) {
            saveDeadLetterInNewTx(evt.eventId(), "TagChangedEvent", toMap(evt), ex);
            log.warn("TagChangedEvent projection failed: eventId={}", evt.eventId(), ex);
        }
    }

    /** 实际处理 TagChangedEvent 投影。失败抛。 */
    public void processTagChangedEvent(TagChangedEvent evt) {
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
                    searchIndexMapper.upsert(builder.buildItemSearchIndex(evt.householdId(), item));
                }
                cursor = page.nextCursor();
                hasMore = page.hasMore();
            }
        });
    }

    // ===== Location 事件 =====

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLocationChanged(LocationChangedEvent evt) {
        try {
            processLocationChangedEvent(evt);
        } catch (RuntimeException ex) {
            saveDeadLetterInNewTx(evt.eventId(), "LocationChangedEvent", toMap(evt), ex);
            log.warn("LocationChangedEvent projection failed: eventId={}", evt.eventId(), ex);
        }
    }

    /** 实际处理 LocationChangedEvent 投影：重建 search_index。失败抛。 */
    public void processLocationChangedEvent(LocationChangedEvent evt) {
        requiresNewTx.executeWithoutResult(status -> {
            int rows = processedEventMapper.insertOnConflictDoNothing(
                    evt.eventId(), "LocationChangedEvent");
            if (rows == 0) return;
            handleLocationChanged(evt);
        });
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
                    searchIndexMapper.upsert(builder.buildLocationSearchIndex(evt.householdId(), loc));
                    break;
                }
            }
            cursor = page.nextCursor();
            hasMore = page.hasMore();
        }
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

    private CatalogApi.ItemInfo resolveItemInfo(UUID householdId, UUID itemId) {
        try {
            return catalogApi.requireItem(householdId, itemId);
        } catch (Exception e) {
            return null;
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
