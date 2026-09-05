package com.zija.ai.internal;

import com.zija.catalog.CatalogApi;
import com.zija.identity.IdentityApi;
import com.zija.inventory.InventoryApi;
import com.zija.location.LocationApi;
import com.zija.reminder.ReminderApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final ReminderApi reminderApi;
    private final Clock clock;

    HouseholdFactQueries(
            CatalogApi catalogApi,
            InventoryApi inventoryApi,
            LocationApi locationApi,
            IdentityApi identityApi,
            ReminderApi reminderApi,
            @Qualifier(AiClockConfig.AI_CLOCK) Clock clock
    ) {
        this.catalogApi = catalogApi;
        this.inventoryApi = inventoryApi;
        this.locationApi = locationApi;
        this.identityApi = identityApi;
        this.reminderApi = reminderApi;
        this.clock = clock;
    }

    /** 按名称关键字搜索活跃物品，返回限量命中；不存在的物品不会出现在结果中。 */
    List<ItemHit> searchItems(UUID householdId, String keyword, int limit, UUID targetItemId) {
        String needle = keyword == null ? "" : keyword.trim();
        var items = catalogApi.searchActiveItemsByName(householdId, needle, targetItemId, limit);
        var unitIds = items.stream()
                .map(CatalogApi.ItemInfo::unitId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        var unitNames = catalogApi.unitNames(householdId, unitIds);
        List<ItemHit> hits = new ArrayList<>();
        for (var item : items) {
            var unit = unitNames.get(item.unitId());
            hits.add(new ItemHit(
                    item.id(), item.name(), unit != null ? unit : "", item.managementType(),
                    item.lowStockMode(), item.lowStockThreshold(),
                    inventoryApi.currentTotalStockOfItem(householdId, item.id())));
        }
        return hits;
    }

    /** 指定物品的库存分布：批次 + 位置 + 数量 + 到期日。 */
    ItemStock itemStock(UUID householdId, UUID itemId) {
        var item = catalogApi.requireItem(householdId, itemId);
        var unitName = unitName(householdId, item.unitId());
        var locationPaths = locationPathMap(householdId);
        var lotNumbers = new LinkedHashMap<UUID, String>();
        var expiryByLot = new LinkedHashMap<UUID, LocalDate>();
        for (var lot : inventoryApi.lotsOfItem(householdId, itemId)) {
            lotNumbers.put(lot.lotId(), lot.lotNumber() == null ? "" : lot.lotNumber());
            expiryByLot.put(lot.lotId(), lot.expiryDate());
        }
        List<Position> positions = new ArrayList<>();
        for (var position : inventoryApi.stockPositionsOfItem(householdId, itemId)) {
            positions.add(new Position(
                    position.lotId(),
                    lotNumbers.getOrDefault(position.lotId(), ""),
                    position.locationId(),
                    locationPaths.getOrDefault(position.locationId(), ""),
                    position.quantity(),
                    expiryByLot.get(position.lotId())));
        }
        return new ItemStock(
                item.id(), item.name(), unitName,
                inventoryApi.currentTotalStockOfItem(householdId, itemId),
                positions);
    }

    /** 指定位置及其子位置中的当前库存，按物品与批次返回有界快照。 */
    LocationStock locationStock(UUID householdId, UUID locationId, String itemKeyword, int limit) {
        locationApi.requireLocation(householdId, locationId);
        String itemNeedle = itemKeyword == null ? "" : itemKeyword.trim();
        var locationPaths = locationPathMap(householdId);
        var locationScope = new LinkedHashSet<UUID>();
        collectLocationScope(locationApi.tree(householdId).roots(), locationId, false, locationScope);
        if (locationScope.isEmpty()) {
            return new LocationStock(locationId, locationPaths.getOrDefault(locationId, ""), List.of());
        }
        List<LocationPosition> positions = inventoryApi.findStockPositionsInLocations(
                        householdId, locationScope, itemNeedle, limit)
                .stream()
                .map(position -> new LocationPosition(
                        position.itemId(), position.itemName(), position.unitName(),
                        position.lotId(), position.lotNumber() == null ? "" : position.lotNumber(),
                        position.locationId(), locationPaths.getOrDefault(position.locationId(), ""),
                        position.quantity(), position.expiryDate()))
                .toList();
        return new LocationStock(locationId, locationPaths.getOrDefault(locationId, ""), positions);
    }

    /** 不限物品的临期批次快照（数量 > 0，到期日在窗口内）。 */
    List<ExpiringLot> expiringLots(
            UUID householdId,
            int withinDays,
            int limit,
            UUID targetItemId,
            UUID targetLotId
    ) {
        LocalDate today = LocalDate.now(clock);
        LocalDate horizon = today.plusDays(Math.max(0, withinDays));
        return inventoryApi.findExpiringLots(
                        householdId, today, horizon, targetItemId, targetLotId, limit)
                .stream()
                .map(lot -> new ExpiringLot(
                        lot.lotId(), lot.itemId(), lot.itemName(),
                        lot.lotNumber() == null ? "" : lot.lotNumber(),
                        lot.expiryDate(),
                        ChronoUnit.DAYS.between(today, lot.expiryDate()),
                        lot.quantity(),
                        lot.unitName() == null ? "" : lot.unitName()))
                .toList();
    }

    /** 不限物品的已过期批次快照（数量 > 0，到期日早于今天）。 */
    List<ExpiringLot> expiredLots(
            UUID householdId,
            int limit,
            UUID targetItemId,
            UUID targetLotId
    ) {
        LocalDate today = LocalDate.now(clock);
        return inventoryApi.findExpiredLots(householdId, today, targetItemId, targetLotId, limit)
                .stream()
                .map(lot -> new ExpiringLot(
                        lot.lotId(), lot.itemId(), lot.itemName(),
                        lot.lotNumber() == null ? "" : lot.lotNumber(),
                        lot.expiryDate(),
                        ChronoUnit.DAYS.between(lot.expiryDate(), today),
                        lot.quantity(),
                        lot.unitName() == null ? "" : lot.unitName()))
                .toList();
    }

    /** 低库存物品快照（阈值模式启用且当前总量低于阈值）。 */
    List<LowStockItem> lowStock(UUID householdId, int limit, UUID targetItemId) {
        return inventoryApi.findLowStockItems(householdId, targetItemId, limit)
                .stream()
                .map(item -> new LowStockItem(
                        item.itemId(), item.itemName(),
                        item.unitName() == null ? "" : item.unitName(),
                        item.currentTotal(), item.threshold()))
                .toList();
    }

    /** 当前家庭待处理提醒任务快照（OPEN/SNOOZED 优先任务，有界）。 */
    List<ReminderTaskFact> reminderTasks(UUID householdId, int limit) {
        var tasks = reminderApi.priorityTasks(householdId, limit);
        var itemIds = tasks.stream()
                .map(ReminderApi.PriorityTaskInfo::itemId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        var itemNames = itemIds.isEmpty()
                ? Map.<UUID, String>of()
                : catalogApi.itemNames(householdId, itemIds);
        var lotNumbers = new LinkedHashMap<UUID, String>();
        for (var task : tasks) {
            if (task.lotId() == null || lotNumbers.containsKey(task.lotId())) {
                continue;
            }
            inventoryApi.findLot(householdId, task.lotId()).ifPresent(lot ->
                    lotNumbers.put(lot.lotId(), lot.lotNumber()));
        }
        List<ReminderTaskFact> results = new ArrayList<>();
        for (var task : tasks) {
            results.add(new ReminderTaskFact(
                    task.taskId(),
                    task.kind(),
                    task.severity(),
                    task.title(),
                    task.dueAt(),
                    task.itemId(),
                    itemNames.getOrDefault(task.itemId(), ""),
                    task.lotId(),
                    task.lotId() == null ? "" : lotNumbers.getOrDefault(task.lotId(), "")));
        }
        return results;
    }

    /** 指定物品最近若干条不可变流水（原因、操作人、时间）。 */
    List<MovementFact> itemMovements(
            UUID householdId,
            UUID itemId,
            int limit,
            UUID targetLotId,
            UUID targetLocationId
    ) {
        var item = catalogApi.requireItem(householdId, itemId);
        var locationPaths = locationPathMap(householdId);
        var movements = inventoryApi.findRecentMovementsOfItem(
                householdId, itemId, targetLotId, targetLocationId, limit);
        var operatorNames = operatorNames(movements);
        List<MovementFact> facts = new ArrayList<>();
        for (var movement : movements) {
            facts.add(new MovementFact(
                    movement.id(), movement.lotId(), item.id(), item.name(),
                    movement.type(), movement.quantity(),
                    movement.reason(),
                    operatorNames.getOrDefault(movement.operatorAccountId(), ""),
                    movement.businessTime(),
                    movement.fromLocationId(), movement.toLocationId(),
                    locationPaths.getOrDefault(movement.fromLocationId(), ""),
                    locationPaths.getOrDefault(movement.toLocationId(), "")));
        }
        return facts;
    }

    private String unitName(UUID householdId, UUID unitId) {
        if (unitId == null) {
            return "";
        }
        return catalogApi.requireUnit(householdId, unitId).name();
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

    private void collectLocationScope(
            List<LocationApi.LocationNode> nodes,
            UUID targetId,
            boolean withinTarget,
            LinkedHashSet<UUID> out
    ) {
        for (var node : nodes) {
            boolean include = withinTarget || targetId.equals(node.id());
            if (include) out.add(node.id());
            collectLocationScope(node.children(), targetId, include, out);
        }
    }

    /** 从有界流水快照收集操作人账户，再批量解析展示名。 */
    private Map<UUID, String> operatorNames(List<InventoryApi.MovementInfo> movements) {
        var accountIds = new LinkedHashSet<UUID>();
        for (var movement : movements) {
            if (movement.operatorAccountId() != null) {
                accountIds.add(movement.operatorAccountId());
            }
        }
        if (accountIds.isEmpty()) {
            return Map.of();
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

    record LocationStock(UUID locationId, String locationPath, List<LocationPosition> positions) {
    }

    record LocationPosition(
            UUID itemId,
            String itemName,
            String unitName,
            UUID lotId,
            String lotNumber,
            UUID locationId,
            String locationPath,
            BigDecimal quantity,
            LocalDate expiryDate
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

    record ReminderTaskFact(
            UUID taskId,
            String kind,
            String severity,
            String title,
            OffsetDateTime dueAt,
            UUID itemId,
            String itemName,
            UUID lotId,
            String lotNumber
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
            UUID fromLocationId,
            UUID toLocationId,
            String fromLocationPath,
            String toLocationPath
    ) {
    }
}
