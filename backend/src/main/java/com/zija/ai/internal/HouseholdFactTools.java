package com.zija.ai.internal;

import com.zija.ai.internal.HouseholdFactQaModels.Collector;
import com.zija.ai.internal.HouseholdFactQaModels.Jump;
import com.zija.ai.internal.HouseholdFactQaModels.StructuredResult;
import com.zija.inventory.InventoryApi;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 家庭事实只读工具集。每个 {@code @Tool} 方法：
 *
 * <ul>
 *   <li>在构造时绑定 {@code householdId}（服务端从认证成员推导），模型不能提供或修改家庭 ID；</li>
 *   <li>只调用 {@link HouseholdFactQueries} 的只读查询契约，不生成 SQL、不调用写入命令、不跨页汇总；</li>
 *   <li>返回供模型阅读的结构化事实（由 Spring AI 序列化为 JSON），同时把确定性结构化结果与权威页面
 *       跳转写入 {@link Collector}，由问答服务原样拼进答案；</li>
 *   <li>查询源抛错时返回显式 {@code UNAVAILABLE} 标记，模型必须据此回答「暂时无法确认」，不得补答。</li>
 * </ul>
 */
final class HouseholdFactTools {

    static final String CATEGORY_HOUSEHOLD_FACT = "HOUSEHOLD_FACT";

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final UUID householdId;
    private final HouseholdFactQueries queries;
    private final Collector collector;
    private final HouseholdFactQaModels.QaTarget target;
    private final InventoryApi inventoryApi;

    HouseholdFactTools(
            UUID householdId,
            HouseholdFactQueries queries,
            Collector collector,
            HouseholdFactQaModels.QaTarget target,
            InventoryApi inventoryApi
    ) {
        this.householdId = householdId;
        this.queries = queries;
        this.collector = collector;
        this.target = target;
        this.inventoryApi = inventoryApi;
    }

    @Tool(description = "在当前家庭中按名称、品牌或标签搜索物品，返回 id、名称、单位、当前总库存和低库存标记。空关键字只返回有界前 N 条")
    Map<String, Object> searchItems(
            @ToolParam(description = "名称、品牌或标签关键字，例如「牛奶」「伊利」「乳制品」") String keyword,
            @ToolParam(description = "最多返回多少条，1-50，选填") Integer limit
    ) {
        int n = boundedLimit(limit);
        if (!collector.beginToolCall()) {
            return unavailableBody("search_items");
        }
        try {
            if (target != null && !isItemTarget()) {
                return unavailable("search_items");
            }
            var hits = queries.searchItems(householdId, keyword == null ? "" : keyword, n, targetItemId());
            if (hits.isEmpty() && isItemTarget()) {
                hits = queries.searchItems(householdId, "", n, targetItemId());
            }
            collector.noteBoundedList(hits.size(), n);
            List<Map<String, String>> rows = hits.stream()
                    .map(hit -> cellMap(
                            "itemId", String.valueOf(hit.itemId()),
                            "名称", hit.name(),
                            "单位", hit.unitName(),
                            "当前总库存", str(hit.currentTotalStock()),
                            "低库存", Boolean.toString(isLowStock(
                                    hit.lowStockMode(), hit.lowStockThreshold(), hit.currentTotalStock()))))
                    .toList();
            collector.addResult(new StructuredResult("ITEM_SEARCH", "物品搜索结果", rows));
            hits.forEach(hit -> collector.addJump(
                    new Jump("ITEM", hit.name(), String.valueOf(hit.itemId()), null, null)));
            return Map.of("items", hits.stream().map(hit -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("itemId", String.valueOf(hit.itemId()));
                body.put("name", hit.name());
                body.put("unitName", hit.unitName());
                body.put("currentTotalStock", str(hit.currentTotalStock()));
                body.put("lowStock", isLowStock(
                        hit.lowStockMode(), hit.lowStockThreshold(), hit.currentTotalStock()));
                return body;
            }).toList());
        } catch (RuntimeException ex) {
            return unavailable("search_items");
        }
    }

    @Tool(description = "在当前家庭按批次号或序列号搜索批次，返回批次 id 和所属物品 id，供物品快照使用。空白序列号不会命中无关关键字。空关键字只返回有界前 N 条。已确认位置时只返回该位置及子位置中有库存的批次")
    Map<String, Object> searchLots(
            @ToolParam(description = "批次号或序列号关键字，例如「LOT-2024-01」") String keyword,
            @ToolParam(description = "最多返回多少条，1-50，选填") Integer limit
    ) {
        int n = boundedLimit(limit);
        if (!collector.beginToolCall()) {
            return unavailableBody("search_lots");
        }
        try {
            UUID itemScope = isItemTarget() ? target.id() : (isLotTarget() ? targetItemId() : null);
            UUID lotScope = isLotTarget() ? target.id() : null;
            var locationScope = isLocationTarget()
                    ? queries.locationScopeIds(householdId, target.id())
                    : null;
            var hits = queries.searchLots(
                    householdId, keyword == null ? "" : keyword, n, itemScope, lotScope, locationScope);
            collector.noteBoundedList(hits.size(), n);
            List<Map<String, String>> rows = hits.stream()
                    .map(hit -> cellMap(
                            "物品", hit.itemName(),
                            "批次号", orDash(hit.lotNumber()),
                            "序列号", orDash(hit.serialNumber())))
                    .toList();
            collector.addResult(new StructuredResult("LOT_SEARCH", "批次搜索结果", rows));
            hits.forEach(hit -> collector.addJump(new Jump(
                    "LOT",
                    lotLabel(hit.itemName(), hit.lotNumber()),
                    hit.itemId().toString(),
                    hit.lotId().toString(),
                    null)));
            return Map.of("lots", hits.stream().map(hit -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("lotId", hit.lotId().toString());
                body.put("itemId", hit.itemId().toString());
                body.put("itemName", hit.itemName());
                body.put("lotNumber", hit.lotNumber());
                body.put("serialNumber", hit.serialNumber());
                return body;
            }).toList());
        } catch (RuntimeException ex) {
            return unavailable("search_lots");
        }
    }

    @Tool(description = "查询某物品快照：总量、库存位、是否低于低库存阈值及阈值、最近到期、最近一条流水。传入批次 id 时，库存位和最近流水只属于该批次，scopedStock 是该批次的数量；totalStock 始终是整件物品所有批次的合计")
    Map<String, Object> itemStock(
            @ToolParam(description = "物品 id") String itemId,
            @ToolParam(description = "批次 id。传入搜索得到的 id 时，库存位和最近流水只属于该批次", required = false) String lotId,
            @ToolParam(description = "最多返回多少条位置分布，1-50，选填") Integer limit
    ) {
        int n = boundedLimit(limit);
        if (!collector.beginToolCall()) {
            return unavailableBody("item_stock");
        }
        try {
            UUID authorizedItem = authorizedItemId(itemId);
            UUID scopedLotId = authorizedLotId(lotId, authorizedItem);
            Set<UUID> locationScope = isLocationTarget()
                    ? queries.locationScopeIds(householdId, target.id())
                    : null;
            var full = queries.itemStock(householdId, authorizedItem);
            var stock = scopeStock(full, scopedLotId, locationScope);
            var shown = stock.positions().stream().limit(n).toList();
            collector.noteBoundedList(stock.positions().size(), n);
            List<Map<String, String>> rows = shown.stream()
                    .map(p -> cellMap("位置", p.locationPath(),
                            "批次号", p.lotNumber(),
                            "数量", str(p.quantity()),
                            "到期日", p.expiryDate() != null ? ISO_DATE.format(p.expiryDate()) : "-"))
                    .toList();
            collector.addResult(new StructuredResult("ITEM_STOCK",
                    "「" + stock.itemName() + "」库存分布", rows));
            boolean lowStock = isLowStock(
                    full.lowStockMode(), full.lowStockThreshold(), full.totalStock());
            String thresholdText = full.lowStockThreshold() == null ? "-" : str(full.lowStockThreshold());
            var nearestExpiry = stock.positions().stream()
                    .map(HouseholdFactQueries.Position::expiryDate)
                    .filter(date -> date != null)
                    .min(java.time.LocalDate::compareTo)
                    .orElse(null);
            String nearestExpiryText = nearestExpiry == null ? "-" : ISO_DATE.format(nearestExpiry);
            boolean scoped = scopedLotId != null || isLocationTarget();
            var totalRow = cellMap(
                    "物品", stock.itemName(),
                    "当前总库存", str(full.totalStock()),
                    "单位", stock.unitName(),
                    "低库存", Boolean.toString(lowStock),
                    "阈值", thresholdText,
                    "最近到期", nearestExpiryText);
            if (scoped) {
                totalRow.put(scopedLotId != null ? "批次数量" : "位置内数量", str(stock.totalStock()));
            }
            collector.addResult(new StructuredResult(
                    "ITEM_STOCK_TOTAL",
                    "「" + stock.itemName() + "」库存总量",
                    List.of(totalRow)));
            collector.addJump(new Jump("ITEM", stock.itemName(),
                    String.valueOf(stock.itemId()), null, null));
            shown.forEach(p -> {
                collector.addJump(new Jump("LOT", p.lotNumber(),
                        String.valueOf(stock.itemId()), String.valueOf(p.lotId()), null));
                if (p.locationId() != null) {
                    collector.addJump(new Jump("LOCATION", p.locationPath(),
                            String.valueOf(stock.itemId()), String.valueOf(p.lotId()),
                            String.valueOf(p.locationId())));
                }
            });
            var movements = queries.itemMovements(
                    householdId,
                    stock.itemId(),
                    1,
                    scopedLotId,
                    locationScope);
            List<Map<String, String>> movementRows = movements.stream()
                    .map(m -> cellMap("类型", m.type(),
                            "数量", str(m.quantity()),
                            "原因", orDash(m.reason()),
                            "操作人", orDash(m.operatorDisplayName()),
                            "时间", m.businessTime() != null ? m.businessTime().toString() : "-",
                            "从", orDash(m.fromLocationPath()),
                            "到", orDash(m.toLocationPath())))
                    .toList();
            collector.addResult(new StructuredResult(
                    "MOVEMENTS", "「" + stock.itemName() + "」最近流水", movementRows));
            if (!movements.isEmpty()) {
                collector.addJump(new Jump(
                        "MOVEMENT", "查看流水", stock.itemId().toString(), null, null));
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("itemName", stock.itemName());
            body.put("unitName", stock.unitName());
            body.put("totalStock", str(full.totalStock()));
            if (scoped) {
                body.put("scopedStock", str(stock.totalStock()));
            }
            body.put("lowStock", lowStock);
            body.put("lowStockThreshold", full.lowStockThreshold() == null ? "" : str(full.lowStockThreshold()));
            body.put("nearestExpiry", nearestExpiry == null ? "" : ISO_DATE.format(nearestExpiry));
            body.put("positions", shown.stream().map(p -> Map.of(
                    "locationPath", p.locationPath(),
                    "lotNumber", p.lotNumber(),
                    "quantity", str(p.quantity()),
                    "expiryDate", p.expiryDate() != null ? ISO_DATE.format(p.expiryDate()) : "")).toList());
            if (movements.isEmpty()) {
                body.put("latestMovement", Map.of());
            } else {
                var movement = movements.getFirst();
                Map<String, Object> latest = new LinkedHashMap<>();
                latest.put("type", movement.type());
                latest.put("quantity", str(movement.quantity()));
                latest.put("reason", orDash(movement.reason()));
                latest.put("operator", orDash(movement.operatorDisplayName()));
                latest.put("businessTime", movement.businessTime() != null
                        ? movement.businessTime().toString() : "");
                latest.put("from", orDash(movement.fromLocationPath()));
                latest.put("to", orDash(movement.toLocationPath()));
                body.put("latestMovement", latest);
            }
            return body;
        } catch (RuntimeException ex) {
            return unavailable("item_stock");
        }
    }

    @Tool(description = "在当前家庭按名称搜索位置，返回 id、名称和路径。空关键字只返回有界前 N 条")
    Map<String, Object> searchLocations(
            @ToolParam(description = "位置名称关键字，例如「厨房」「冰箱」") String keyword,
            @ToolParam(description = "最多返回多少条，1-50，选填") Integer limit
    ) {
        int n = boundedLimit(limit);
        if (!collector.beginToolCall()) {
            return unavailableBody("search_locations");
        }
        try {
            if (isItemTarget() || isLotTarget()) {
                return unavailable("search_locations");
            }
            var hits = queries.searchLocations(
                    householdId, keyword == null ? "" : keyword, n, isLocationTarget() ? target.id() : null);
            collector.noteBoundedList(hits.size(), n);
            List<Map<String, String>> rows = hits.stream()
                    .map(hit -> cellMap("名称", hit.name(), "路径", hit.path()))
                    .toList();
            collector.addResult(new StructuredResult("LOCATION_SEARCH", "位置搜索结果", rows));
            hits.forEach(hit -> collector.addJump(
                    new Jump("LOCATION", hit.path(), null, null, hit.locationId().toString())));
            return Map.of("locations", hits.stream().map(hit -> {
                Map<String, Object> body = new LinkedHashMap<String, Object>();
                body.put("locationId", hit.locationId().toString());
                body.put("name", hit.name());
                body.put("path", hit.path());
                return body;
            }).toList());
        } catch (RuntimeException ex) {
            return unavailable("search_locations");
        }
    }

    @Tool(description = "查询某位置及其子位置中的当前库存，返回物品、批次、位置、数量与到期日。位置 id 可省略，省略时使用服务端已确认的位置")
    Map<String, Object> locationStock(
            @ToolParam(description = "位置 id。未确认位置时传入搜索得到的 id；已确认位置时可以省略", required = false) String locationId,
            @ToolParam(description = "物品名称关键字，未指定则返回该位置内全部物品，选填") String itemKeyword,
            @ToolParam(description = "最多返回多少条库存位，1-50，选填") Integer limit
    ) {
        int n = boundedLimit(limit);
        if (!collector.beginToolCall()) {
            return unavailableBody("location_stock");
        }
        try {
            var stock = queries.locationStock(householdId, authorizedLocationId(locationId), itemKeyword, n);
            collector.noteBoundedList(stock.positions().size(), n);
            List<Map<String, String>> rows = stock.positions().stream()
                    .map(position -> cellMap(
                            "物品", position.itemName(),
                            "批次号", orDash(position.lotNumber()),
                            "位置", position.locationPath(),
                            "数量", str(position.quantity()),
                            "单位", position.unitName(),
                            "到期日", position.expiryDate() != null
                                    ? ISO_DATE.format(position.expiryDate()) : "-"))
                    .toList();
            collector.addResult(new StructuredResult(
                    "LOCATION_STOCK", "「" + stock.locationPath() + "」当前库存", rows));
            stock.positions().forEach(position -> {
                collector.addJump(new Jump(
                        "ITEM", position.itemName(), position.itemId().toString(), null, null));
                collector.addJump(new Jump(
                        "LOT", orDash(position.lotNumber()), position.itemId().toString(),
                        position.lotId().toString(), null));
                if (position.locationId() != null) {
                    collector.addJump(new Jump(
                            "LOCATION", position.locationPath(), null, null, position.locationId().toString()));
                }
            });
            collector.addJump(new Jump(
                    "LOCATION", stock.locationPath(), null, null, stock.locationId().toString()));
            return Map.of(
                    "locationPath", stock.locationPath(),
                    "positions", stock.positions().stream().map(position -> Map.of(
                            "itemName", position.itemName(),
                            "lotNumber", orDash(position.lotNumber()),
                            "locationPath", position.locationPath(),
                            "quantity", str(position.quantity()),
                            "unitName", position.unitName(),
                            "expiryDate", position.expiryDate() != null
                                    ? ISO_DATE.format(position.expiryDate()) : ""
                    )).toList());
        } catch (RuntimeException ex) {
            return unavailable("location_stock");
        }
    }

    @Tool(description = "查询当前家庭在指定天数内到期的临期批次（含物品、批次号、到期日、剩余数量）。已确认位置时只返回该位置及子位置中的数量")
    Map<String, Object> expiringLots(
            @ToolParam(description = "未来多少天内到期，例如 30，选填") Integer withinDays,
            @ToolParam(description = "最多返回多少条，1-50，选填") Integer limit
    ) {
        int days = withinDays == null ? 30 : Math.max(1, withinDays);
        int n = boundedLimit(limit);
        if (!collector.beginToolCall()) {
            return unavailableBody("expiring_lots");
        }
        try {
            if (isLotTarget() && targetItemId() == null) {
                return unavailable("expiring_lots");
            }
            var locationIds = isLocationTarget()
                    ? queries.locationScopeIds(householdId, target.id())
                    : null;
            var lots = queries.expiringLots(
                    householdId, days, n, targetItemId(), isLotTarget() ? target.id() : null, locationIds);
            collector.noteBoundedList(lots.size(), n);
            List<Map<String, String>> rows = lots.stream()
                    .map(lot -> cellMap("物品", lot.itemName(),
                            "批次号", lot.lotNumber(),
                            "到期日", ISO_DATE.format(lot.expiryDate()),
                            "剩余天数", String.valueOf(lot.daysUntilExpiry()),
                            "数量", str(lot.quantity()),
                            "单位", lot.unitName()))
                    .toList();
            collector.addResult(new StructuredResult("EXPIRING_LOTS", "临期批次", rows));
            lots.forEach(lot -> {
                collector.addJump(new Jump("LOT", lot.itemName() + " " + lot.lotNumber(),
                        String.valueOf(lot.itemId()), String.valueOf(lot.lotId()), null));
                collector.addJump(new Jump("ITEM", lot.itemName(),
                        String.valueOf(lot.itemId()), String.valueOf(lot.lotId()), null));
            });
            if (!lots.isEmpty()) {
                collector.addJump(new Jump("REMINDER", "查看临期提醒", null, null, null, null, "EXPIRY"));
            }
            return Map.of("expiringLots", lots.stream().map(lot -> Map.of(
                    "itemName", lot.itemName(),
                    "lotNumber", lot.lotNumber(),
                    "expiryDate", ISO_DATE.format(lot.expiryDate()),
                    "daysUntilExpiry", String.valueOf(lot.daysUntilExpiry()),
                    "quantity", str(lot.quantity()))).toList());
        } catch (RuntimeException ex) {
            return unavailable("expiring_lots");
        }
    }

    @Tool(description = "查询当前家庭已经过期但仍有库存的批次（含物品、批次号、到期日、已过期天数）。不含尚未到期的临期批次。已确认位置时只返回该位置及子位置中的数量")
    Map<String, Object> expiredLots(
            @ToolParam(description = "最多返回多少条，1-50，选填") Integer limit
    ) {
        int n = boundedLimit(limit);
        if (!collector.beginToolCall()) {
            return unavailableBody("expired_lots");
        }
        try {
            if (isLotTarget() && targetItemId() == null) {
                return unavailable("expired_lots");
            }
            var locationIds = isLocationTarget()
                    ? queries.locationScopeIds(householdId, target.id())
                    : null;
            var lots = queries.expiredLots(
                    householdId, n, targetItemId(), isLotTarget() ? target.id() : null, locationIds);
            collector.noteBoundedList(lots.size(), n);
            List<Map<String, String>> rows = lots.stream()
                    .map(lot -> cellMap("物品", lot.itemName(),
                            "批次号", lot.lotNumber(),
                            "到期日", ISO_DATE.format(lot.expiryDate()),
                            "已过期天数", String.valueOf(lot.daysUntilExpiry()),
                            "数量", str(lot.quantity()),
                            "单位", lot.unitName()))
                    .toList();
            collector.addResult(new StructuredResult("EXPIRED_LOTS", "已过期批次", rows));
            lots.forEach(lot -> {
                collector.addJump(new Jump("LOT", lot.itemName() + " " + lot.lotNumber(),
                        String.valueOf(lot.itemId()), String.valueOf(lot.lotId()), null));
                collector.addJump(new Jump("ITEM", lot.itemName(),
                        String.valueOf(lot.itemId()), String.valueOf(lot.lotId()), null));
            });
            if (!lots.isEmpty()) {
                collector.addJump(new Jump("REMINDER", "查看过期提醒", null, null, null, null, "EXPIRY"));
            }
            return Map.of("expiredLots", lots.stream().map(lot -> Map.of(
                    "itemName", lot.itemName(),
                    "lotNumber", lot.lotNumber(),
                    "expiryDate", ISO_DATE.format(lot.expiryDate()),
                    "daysOverdue", String.valueOf(lot.daysUntilExpiry()),
                    "quantity", str(lot.quantity()))).toList());
        } catch (RuntimeException ex) {
            return unavailable("expired_lots");
        }
    }

    @Tool(description = "查询当前家庭低于低库存阈值的物品（含名称、当前库存、阈值）")
    Map<String, Object> lowStock(
            @ToolParam(description = "最多返回多少条，1-50，选填") Integer limit
    ) {
        int n = boundedLimit(limit);
        if (!collector.beginToolCall()) {
            return unavailableBody("low_stock");
        }
        try {
            if (target != null && !isItemTarget()) {
                return unavailable("low_stock");
            }
            var items = queries.lowStock(householdId, n, targetItemId());
            collector.noteBoundedList(items.size(), n);
            List<Map<String, String>> rows = items.stream()
                    .map(item -> cellMap("物品", item.itemName(),
                            "单位", item.unitName(),
                            "当前库存", str(item.currentTotal()),
                            "阈值", str(item.threshold())))
                    .toList();
            collector.addResult(new StructuredResult("LOW_STOCK", "低库存物品", rows));
            items.forEach(item -> collector.addJump(
                    new Jump("ITEM", item.itemName(), String.valueOf(item.itemId()), null, null)));
            if (!items.isEmpty()) {
                collector.addJump(new Jump("REMINDER", "查看低库存提醒", null, null, null, null, "LOW_STOCK"));
            }
            return Map.of("lowStock", items.stream().map(item -> Map.of(
                    "itemName", item.itemName(),
                    "currentTotal", str(item.currentTotal()),
                    "threshold", str(item.threshold()))).toList());
        } catch (RuntimeException ex) {
            return unavailable("low_stock");
        }
    }

    @Tool(description = "查询当前家庭待处理的提醒任务（临期、低库存），返回类型、严重程度、标题、到期时间与关联物品/批次。不回答提醒规则如何配置。")
    Map<String, Object> openReminderTasks(
            @ToolParam(description = "最多返回多少条，1-50，选填") Integer limit
    ) {
        int n = boundedLimit(limit);
        if (!collector.beginToolCall()) {
            return unavailableBody("open_reminder_tasks");
        }
        try {
            var tasks = queries.reminderTasks(householdId, n);
            collector.noteBoundedList(tasks.size(), n);
            List<Map<String, String>> rows = tasks.stream()
                    .map(task -> cellMap(
                            "类型", localizeReminderKind(task.kind()),
                            "严重程度", localizeReminderSeverity(task.severity()),
                            "标题", task.title(),
                            "到期时间", task.dueAt() != null ? task.dueAt().toString() : "-",
                            "物品", orDash(task.itemName()),
                            "批次", orDash(task.lotNumber())))
                    .toList();
            collector.addResult(new StructuredResult("REMINDER_TASKS", "待处理提醒", rows));
            collector.addJump(new Jump("REMINDER", "查看提醒中心", null, null, null));
            return Map.of("reminderTasks", tasks.stream().map(task -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("kind", localizeReminderKind(task.kind()));
                body.put("severity", localizeReminderSeverity(task.severity()));
                body.put("title", task.title());
                body.put("dueAt", task.dueAt() != null ? task.dueAt().toString() : "");
                body.put("itemName", orDash(task.itemName()));
                body.put("lotNumber", orDash(task.lotNumber()));
                return body;
            }).toList());
        } catch (RuntimeException ex) {
            return unavailable("open_reminder_tasks");
        }
    }

    @Tool(description = "查询库存流水，作为不可变事实：类型、数量、原因、操作人展示名、业务时间、来源和目标位置。可按物品、批次、位置和起止日期过滤。位置按来源或目标命中，并包含子位置。未传起止日期时返回最近若干条；传入区间时按家庭时区解析，最长 90 个自然日，结果里写出实际起止日期。物品 id 与位置 id 至少提供一个，已确认目标时可省略对应 id。不要把截断结果自行加总。")
    Map<String, Object> itemMovements(
            @ToolParam(description = "物品 id。按位置查询且不限定物品时可以省略", required = false) String itemId,
            @ToolParam(description = "最多返回多少条，1-50，选填", required = false) Integer limit,
            @ToolParam(description = "批次 id，选填", required = false) String lotId,
            @ToolParam(description = "位置 id。来源或目标任一命中即出，包含子位置。已确认位置时可以省略", required = false) String locationId,
            @ToolParam(description = "起始日期，yyyy-MM-dd，按家庭时区。须与结束日期同时传入", required = false) String fromDate,
            @ToolParam(description = "结束日期，yyyy-MM-dd，按家庭时区，含当天。须与起始日期同时传入；跨度超过 90 个自然日会从结束日往前截断", required = false) String toDate
    ) {
        int n = boundedLimit(limit);
        if (!collector.beginToolCall()) {
            return unavailableBody("item_movements");
        }
        try {
            UUID authorizedItemId = resolveMovementItemId(itemId);
            UUID authorizedLocationId = resolveMovementLocationId(locationId);
            if (authorizedItemId == null && authorizedLocationId == null) {
                throw new IllegalArgumentException("流水查询需要物品或位置");
            }
            UUID authorizedLotId = resolveMovementLotId(lotId, authorizedItemId);
            var query = queries.queryMovements(
                    householdId,
                    authorizedItemId,
                    authorizedLotId,
                    authorizedLocationId,
                    fromDate,
                    toDate,
                    n);
            collector.noteBoundedList(query.movements().size(), n);
            List<Map<String, String>> rows = query.movements().stream()
                    .map(HouseholdFactTools::movementRow)
                    .toList();
            String title = movementTitle(query.itemName(), query.locationPath(), query.from(), query.to());
            collector.addResult(new StructuredResult("MOVEMENTS", title, rows));
            if (authorizedItemId != null) {
                String itemLabel = query.itemName().isBlank() ? authorizedItemId.toString() : query.itemName();
                collector.addJump(new Jump("ITEM", itemLabel, authorizedItemId.toString(), null, null));
            }
            if (authorizedLocationId != null) {
                String locationLabel = query.locationPath().isBlank()
                        ? authorizedLocationId.toString() : query.locationPath();
                collector.addJump(new Jump(
                        "LOCATION", locationLabel, null, null, authorizedLocationId.toString()));
            }
            if (!rows.isEmpty()) {
                collector.addJump(new Jump(
                        "MOVEMENT",
                        "查看流水",
                        authorizedItemId == null ? null : authorizedItemId.toString(),
                        null,
                        authorizedLocationId == null ? null : authorizedLocationId.toString()));
            }
            return Map.of(
                    "fromDate", query.from() == null ? "" : ISO_DATE.format(query.from()),
                    "toDate", query.to() == null ? "" : ISO_DATE.format(query.to()),
                    "movements", query.movements().stream().map(m -> {
                        Map<String, Object> body = new LinkedHashMap<>();
                        body.put("itemName", m.itemName());
                        body.put("type", m.type());
                        body.put("quantity", str(m.quantity()));
                        body.put("reason", orDash(m.reason()));
                        body.put("operator", orDash(m.operatorDisplayName()));
                        body.put("businessTime", m.businessTime() != null ? m.businessTime().toString() : "");
                        body.put("from", orDash(m.fromLocationPath()));
                        body.put("to", orDash(m.toLocationPath()));
                        return body;
                    }).toList());
        } catch (RuntimeException ex) {
            return unavailable("item_movements");
        }
    }

    private static Map<String, String> movementRow(HouseholdFactQueries.MovementFact movement) {
        return cellMap(
                "物品", orDash(movement.itemName()),
                "类型", movement.type(),
                "数量", str(movement.quantity()),
                "原因", orDash(movement.reason()),
                "操作人", orDash(movement.operatorDisplayName()),
                "时间", movement.businessTime() != null ? movement.businessTime().toString() : "-",
                "从", orDash(movement.fromLocationPath()),
                "到", orDash(movement.toLocationPath()));
    }

    private static String movementTitle(String itemName, String locationPath, LocalDate from, LocalDate to) {
        boolean hasItem = itemName != null && !itemName.isBlank();
        boolean hasLocation = locationPath != null && !locationPath.isBlank();
        String subject;
        if (hasItem && hasLocation) {
            subject = "「" + itemName + " · " + locationPath + "」";
        } else if (hasItem) {
            subject = "「" + itemName + "」";
        } else if (hasLocation) {
            subject = "「" + locationPath + "」";
        } else {
            subject = "流水";
        }
        if (from == null || to == null) {
            return subject + "最近流水";
        }
        return subject + "流水（" + ISO_DATE.format(from) + " 至 " + ISO_DATE.format(to) + "）";
    }

    private UUID resolveMovementItemId(String requested) {
        if (requested == null || requested.isBlank()) {
            return targetItemId();
        }
        UUID requestedId = UUID.fromString(requested.trim());
        UUID scopedItemId = targetItemId();
        if (scopedItemId != null && !scopedItemId.equals(requestedId)) {
            throw new IllegalArgumentException("模型请求超出已确认的问答范围");
        }
        return requestedId;
    }

    private UUID resolveMovementLotId(String requested, UUID itemId) {
        if (isLotTarget()) {
            if (requested != null && !requested.isBlank()
                    && !target.id().equals(UUID.fromString(requested.trim()))) {
                throw new IllegalArgumentException("模型请求超出已确认的问答范围");
            }
            if (itemId != null) {
                return requireLotOfItem(target.id(), itemId);
            }
            return target.id();
        }
        if (requested == null || requested.isBlank()) {
            return null;
        }
        UUID lotId = UUID.fromString(requested.trim());
        if (itemId == null) {
            return inventoryApi.findLot(householdId, lotId)
                    .orElseThrow(() -> new IllegalArgumentException("批次不存在或不属于当前家庭"))
                    .lotId();
        }
        return requireLotOfItem(lotId, itemId);
    }

    private UUID resolveMovementLocationId(String requested) {
        if (isLocationTarget()) {
            return authorizedLocationId(requested);
        }
        if (requested == null || requested.isBlank()) {
            return null;
        }
        return UUID.fromString(requested.trim());
    }

    private static int boundedLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private boolean isItemTarget() {
        return target != null && "ITEM".equals(target.type());
    }

    private boolean isLotTarget() {
        return target != null && "LOT".equals(target.type());
    }

    private boolean isLocationTarget() {
        return target != null && "LOCATION".equals(target.type());
    }

    private UUID targetItemId() {
        if (isItemTarget()) {
            return target.id();
        }
        if (isLotTarget() && inventoryApi != null) {
            return inventoryApi.findLot(householdId, target.id())
                    .map(InventoryApi.LotFlat::itemId)
                    .orElse(null);
        }
        return null;
    }

    private static HouseholdFactQueries.ItemStock scopeStock(
            HouseholdFactQueries.ItemStock stock,
            UUID lotId,
            Set<UUID> locationIds
    ) {
        boolean filterLot = lotId != null;
        boolean filterLocation = locationIds != null;
        if (!filterLot && !filterLocation) {
            return stock;
        }
        var positions = stock.positions().stream()
                .filter(position -> !filterLot || lotId.equals(position.lotId()))
                .filter(position -> !filterLocation || locationIds.contains(position.locationId()))
                .toList();
        BigDecimal total = positions.stream()
                .map(HouseholdFactQueries.Position::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new HouseholdFactQueries.ItemStock(
                stock.itemId(), stock.itemName(), stock.unitName(), total, positions,
                stock.lowStockMode(), stock.lowStockThreshold());
    }

    private UUID authorizedLocationId(String requested) {
        if (isItemTarget() || isLotTarget()) {
            throw new IllegalArgumentException("模型请求超出已确认的问答范围");
        }
        UUID requestedId = requested == null || requested.isBlank() ? null : UUID.fromString(requested.trim());
        if (isLocationTarget()) {
            if (requestedId == null) {
                return target.id();
            }
            if (!queries.locationScopeIds(householdId, target.id()).contains(requestedId)) {
                throw new IllegalArgumentException("模型请求超出已确认的问答范围");
            }
            return requestedId;
        }
        if (requestedId == null) {
            throw new IllegalArgumentException("缺少位置");
        }
        return requestedId;
    }

    private UUID authorizedLotId(String requested, UUID itemId) {
        if (requested == null || requested.isBlank()) {
            if (!isLotTarget()) {
                return null;
            }
            return requireLotOfItem(target.id(), itemId);
        }
        UUID lotId = UUID.fromString(requested.trim());
        if (isLotTarget() && !target.id().equals(lotId)) {
            throw new IllegalArgumentException("模型请求超出已确认的问答范围");
        }
        return requireLotOfItem(lotId, itemId);
    }

    private UUID requireLotOfItem(UUID lotId, UUID itemId) {
        var lot = inventoryApi.findLot(householdId, lotId)
                .orElseThrow(() -> new IllegalArgumentException("批次不存在或不属于当前家庭"));
        if (!lot.itemId().equals(itemId)) {
            throw new IllegalArgumentException("模型请求超出已确认的问答范围");
        }
        return lotId;
    }

    private static String lotLabel(String itemName, String lotNumber) {
        if (lotNumber == null || lotNumber.isBlank()) {
            return itemName + "的批次";
        }
        return itemName + " · " + lotNumber;
    }

    private UUID authorizedItemId(String requested) {
        UUID requestedId = UUID.fromString(requested);
        UUID scopedItemId = targetItemId();
        if (scopedItemId != null && !scopedItemId.equals(requestedId)) {
            throw new IllegalArgumentException("模型请求超出已确认的问答范围");
        }
        return scopedItemId != null ? scopedItemId : requestedId;
    }

    private Map<String, Object> unavailable(String tool) {
        collector.markFactSourceUnavailable();
        return unavailableBody(tool);
    }

    private Map<String, Object> unavailableBody(String tool) {
        var body = new LinkedHashMap<String, Object>();
        body.put("status", "UNAVAILABLE");
        body.put("detail", "家庭事实来源暂时不可用，无法确认（tool=" + tool + "）");
        return body;
    }

    private static String localizeReminderKind(String kind) {
        if ("EXPIRY".equals(kind)) {
            return "临期";
        }
        if ("LOW_STOCK".equals(kind)) {
            return "低库存";
        }
        return orDash(kind);
    }

    private static String localizeReminderSeverity(String severity) {
        return switch (severity == null ? "" : severity) {
            case "URGENT" -> "紧急";
            case "WARN" -> "警告";
            case "INFO" -> "提示";
            default -> orDash(severity);
        };
    }

    private static boolean isLowStock(String mode, BigDecimal threshold, BigDecimal total) {
        return "CUSTOM".equals(mode)
                && threshold != null
                && total != null
                && total.compareTo(threshold) < 0;
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String str(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static Map<String, String> cellMap(String... kv) {
        var map = new LinkedHashMap<String, String>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }
}
