package com.zija.ai.internal;

import com.zija.catalog.CatalogApi;
import com.zija.identity.IdentityApi;
import com.zija.inventory.InventoryApi;
import com.zija.location.LocationApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 家庭事实只读查询契约（受控查询来源）。
 *
 * <p>所有方法都以 {@code householdId} 作为显式参数，只调用各业务模块的公开只读查询端口，
 * 从不接受模型传入的家庭 ID、生成 SQL 或调用写入命令。结果是有界快照（限量、不再翻页聚合），
 * 由服务端在回答时以当前时间作为数据时间。日期边界逻辑使用家庭时区专用 Clock。</p>
 */
@Component
class HouseholdFactQueries {

    private final CatalogApi catalogApi;
    private final InventoryApi inventoryApi;
    private final LocationApi locationApi;
    private final IdentityApi identityApi;
    private final Clock clock;

    HouseholdFactQueries(
            CatalogApi catalogApi,
            InventoryApi inventoryApi,
            LocationApi locationApi,
            IdentityApi identityApi,
            @Qualifier(AiClockConfig.AI_CLOCK) Clock clock
    ) {
        this.catalogApi = catalogApi;
        this.inventoryApi = inventoryApi;
        this.locationApi = locationApi;
        this.identityApi = identityApi;
        this.clock = clock;
    }

    /** 按名称关键字搜索活跃物品，返回限量命中；不存在的物品不会出现在结果中。 */
    List<ItemHit> searchItems(UUID householdId, String keyword, int limit) {
        String needle = keyword == null ? "" : keyword.trim().toLowerCase();
        var unitNames = unitNameMap(householdId);
        List<ItemHit> hits = new ArrayList<>();
        for (var item : catalogApi.listActiveItems(householdId)) {
            if (!needle.isEmpty() && !item.name().toLowerCase().contains(needle)) {
                continue;
            }
            var unit = unitNames.get(item.unitId());
            var hit = new ItemHit(
                    item.id(), item.name(), unit != null ? unit : "", item.managementType(),
                    item.lowStockMode(), item.lowStockThreshold(),
                    inventoryApi.currentTotalStockOfItem(householdId, item.id()));
            hits.add(hit);
            if (hits.size() >= limit) {
                break;
            }
        }
        return hits;
    }

    /** 指定物品的库存分布：批次 + 位置 + 数量 + 到期日。 */
    ItemStock itemStock(UUID householdId, UUID itemId) {
        var item = catalogApi.requireItem(householdId, itemId);
        var unitName = unitName(householdId, item.unitId());
        var locationPaths = locationPathMap(householdId);
        var lotNumbers = new LinkedHashMap<UUID, String>();
        var serialNumbers = new LinkedHashMap<UUID, String>();
        for (var lot : inventoryApi.lotsOfItem(householdId, itemId)) {
            inventoryApi.findLot(householdId, lot.lotId()).ifPresent(found -> {
                lotNumbers.put(found.lotId(), found.lotNumber());
                serialNumbers.put(found.lotId(), found.serialNumber());
            });
        }
        List<Position> positions = new ArrayList<>();
        for (var position : inventoryApi.stockPositionsOfItem(householdId, itemId)) {
            var lot = inventoryApi.findLot(householdId, position.lotId()).orElse(null);
            positions.add(new Position(
                    position.lotId(),
                    lotNumbers.getOrDefault(position.lotId(), ""),
                    position.locationId(),
                    locationPaths.getOrDefault(position.locationId(), ""),
                    position.quantity(),
                    lot != null ? lot.expiryDate() : null));
        }
        return new ItemStock(
                item.id(), item.name(), unitName,
                inventoryApi.currentTotalStockOfItem(householdId, itemId),
                positions);
    }

    /** 不限物品的临期批次快照（数量 > 0，到期日在窗口内）。 */
    List<ExpiringLot> expiringLots(UUID householdId, int withinDays, int limit) {
        var unitNames = unitNameMap(householdId);
        LocalDate today = LocalDate.now(clock);
        LocalDate horizon = today.plusDays(Math.max(0, withinDays));
        List<ExpiringLot> results = new ArrayList<>();
        for (var item : catalogApi.listActiveItems(householdId)) {
            for (var lot : inventoryApi.lotsOfItem(householdId, item.id())) {
                if (lot.expiryDate() == null
                        || lot.expiryDate().isBefore(today)
                        || lot.expiryDate().isAfter(horizon)
                        || lot.totalQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                var detail = inventoryApi.findLot(householdId, lot.lotId()).orElse(null);
                results.add(new ExpiringLot(
                        lot.lotId(), item.id(), item.name(),
                        detail != null ? detail.lotNumber() : "",
                        lot.expiryDate(),
                        ChronoUnit.DAYS.between(today, lot.expiryDate()),
                        lot.totalQuantity(),
                        unitNames.getOrDefault(item.unitId(), "")));
                if (results.size() >= limit) {
                    return results;
                }
            }
        }
        return results;
    }

    /** 低库存物品快照（阈值模式启用且当前总量低于阈值）。 */
    List<LowStockItem> lowStock(UUID householdId, int limit) {
        var unitNames = unitNameMap(householdId);
        List<LowStockItem> results = new ArrayList<>();
        for (var item : catalogApi.listActiveItems(householdId)) {
            if (!"CUSTOM".equals(item.lowStockMode()) || item.lowStockThreshold() == null) {
                continue;
            }
            var current = inventoryApi.currentTotalStockOfItem(householdId, item.id());
            if (current.compareTo(item.lowStockThreshold()) < 0) {
                results.add(new LowStockItem(
                        item.id(), item.name(), unitNames.getOrDefault(item.unitId(), ""),
                        current, item.lowStockThreshold()));
                if (results.size() >= limit) {
                    return results;
                }
            }
        }
        return results;
    }

    /** 指定物品最近若干条不可变流水（原因、操作人、时间）。 */
    List<MovementFact> itemMovements(UUID householdId, UUID itemId, int limit) {
        var item = catalogApi.requireItem(householdId, itemId);
        var locationPaths = locationPathMap(householdId);
        var operatorNames = collectOperatorNames(householdId, itemId);
        List<MovementFact> all = new ArrayList<>();
        for (var lot : inventoryApi.lotsOfItem(householdId, itemId)) {
            for (var movement : inventoryApi.movementsOfLot(householdId, lot.lotId())) {
                all.add(new MovementFact(
                        movement.id(), movement.lotId(), item.id(), item.name(),
                        movement.type(), movement.quantity(),
                        movement.reason(),
                        operatorNames.getOrDefault(movement.operatorAccountId(), ""),
                        movement.businessTime(),
                        locationPaths.getOrDefault(movement.fromLocationId(), ""),
                        locationPaths.getOrDefault(movement.toLocationId(), "")));
            }
        }
        all.sort(Comparator.comparing(MovementFact::businessTime).reversed());
        return all.subList(0, Math.min(all.size(), limit));
    }

    private String unitName(UUID householdId, UUID unitId) {
        if (unitId == null) {
            return "";
        }
        return catalogApi.requireUnit(householdId, unitId).name();
    }

    private Map<UUID, String> unitNameMap(UUID householdId) {
        var map = new LinkedHashMap<UUID, String>();
        for (var item : catalogApi.listActiveItems(householdId)) {
            if (item.unitId() != null) {
                map.putIfAbsent(item.unitId(), unitName(householdId, item.unitId()));
            }
        }
        return map;
    }

    private Map<UUID, String> locationPathMap(UUID householdId) {
        var map = new LinkedHashMap<UUID, String>();
        collectLocationPaths(locationApi.tree(householdId).roots(), "", map);
        return map;
    }

    private void collectLocationPaths(List<LocationApi.LocationNode> nodes, String prefix, Map<UUID, String> out) {
        for (var node : nodes) {
            String path = prefix.isEmpty() ? node.name() : prefix + " / " + node.name();
            out.put(node.id(), path);
            collectLocationPaths(node.children(), path, out);
        }
    }

    /** 从某物品全部流水中收集操作人账户，再批量解析展示名（按需，避免全局遍历）。 */
    private Map<UUID, String> collectOperatorNames(UUID householdId, UUID itemId) {
        var accountIds = new LinkedHashSet<UUID>();
        for (var lot : inventoryApi.lotsOfItem(householdId, itemId)) {
            for (var movement : inventoryApi.movementsOfLot(householdId, lot.lotId())) {
                accountIds.add(movement.operatorAccountId());
            }
        }
        var details = identityApi.findByIds(accountIds);
        var map = new LinkedHashMap<UUID, String>();
        details.forEach((id, account) -> {
            String display = account.displayName() != null && !account.displayName().isBlank()
                    ? account.displayName() : account.username();
            map.put(id, display);
        });
        return map;
    }

    // ---------- 只读事实记录 ----------

    record ItemHit(
            UUID itemId,
            String name,
            String unitName,
            String managementType,
            String lowStockMode,
            BigDecimal lowStockThreshold,
            BigDecimal currentTotalStock
    ) {
    }

    record ItemStock(
            UUID itemId,
            String itemName,
            String unitName,
            BigDecimal totalStock,
            List<Position> positions
    ) {
    }

    record Position(
            UUID lotId,
            String lotNumber,
            UUID locationId,
            String locationPath,
            BigDecimal quantity,
            LocalDate expiryDate
    ) {
    }

    record ExpiringLot(
            UUID lotId,
            UUID itemId,
            String itemName,
            String lotNumber,
            LocalDate expiryDate,
            long daysUntilExpiry,
            BigDecimal quantity,
            String unitName
    ) {
    }

    record LowStockItem(
            UUID itemId,
            String itemName,
            String unitName,
            BigDecimal currentTotal,
            BigDecimal threshold
    ) {
    }

    record MovementFact(
            UUID movementId,
            UUID lotId,
            UUID itemId,
            String itemName,
            String type,
            BigDecimal quantity,
            String reason,
            String operatorDisplayName,
            OffsetDateTime businessTime,
            String fromLocationPath,
            String toLocationPath
    ) {
    }
}
