package com.zija.ai.internal;

import com.zija.ai.internal.HouseholdFactQaModels.Collector;
import com.zija.ai.internal.HouseholdFactQaModels.Jump;
import com.zija.ai.internal.HouseholdFactQaModels.StructuredResult;
import com.zija.inventory.InventoryApi;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Tool(description = "在当前家庭中按名称关键字搜索物品，返回命中物品的 id、名称、单位与当前总库存")
    Map<String, Object> searchItems(
            @ToolParam(description = "物品名称关键字，例如「牛奶」") String keyword,
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
            List<Map<String, String>> rows = hits.stream()
                    .map(hit -> cellMap(
                            "itemId", String.valueOf(hit.itemId()),
                            "名称", hit.name(),
                            "单位", hit.unitName(),
                            "当前总库存", str(hit.currentTotalStock()),
                            "低库存", Boolean.toString(hit.lowStockMode() != null
                                    && "CUSTOM".equals(hit.lowStockMode())
                                    && hit.lowStockThreshold() != null
                                    && hit.currentTotalStock().compareTo(hit.lowStockThreshold()) < 0)))
                    .toList();
            collector.addResult(new StructuredResult("ITEM_SEARCH", "物品搜索结果", rows));
            hits.forEach(hit -> collector.addJump(
                    new Jump("ITEM", hit.name(), String.valueOf(hit.itemId()), null, null)));
            return Map.of("items", hits.stream().map(hit -> Map.of(
                    "itemId", String.valueOf(hit.itemId()),
                    "name", hit.name(),
                    "unitName", hit.unitName(),
                    "currentTotalStock", str(hit.currentTotalStock()))).toList());
        } catch (RuntimeException ex) {
            return unavailable("search_items");
        }
    }

    @Tool(description = "查询某物品的库存分布：每个批次在每个位置的数量、位置路径与批次到期日")
    Map<String, Object> itemStock(
            @ToolParam(description = "物品 id") String itemId,
            @ToolParam(description = "最多返回多少条位置分布，1-50，选填") Integer limit
    ) {
        int n = boundedLimit(limit);
        if (!collector.beginToolCall()) {
            return unavailableBody("item_stock");
        }
        try {
            var stock = scopeStock(queries.itemStock(householdId, authorizedItemId(itemId)));
            List<Map<String, String>> rows = stock.positions().stream()
                    .limit(n)
                    .map(p -> cellMap("位置", p.locationPath(),
                            "批次号", p.lotNumber(),
                            "数量", str(p.quantity()),
                            "到期日", p.expiryDate() != null ? ISO_DATE.format(p.expiryDate()) : "-"))
                    .toList();
            collector.addResult(new StructuredResult("ITEM_STOCK",
                    "「" + stock.itemName() + "」库存分布", rows));
            collector.addResult(new StructuredResult(
                    "ITEM_STOCK_TOTAL",
                    "「" + stock.itemName() + "」库存总量",
                    List.of(cellMap(
                            "物品", stock.itemName(),
                            "当前总库存", str(stock.totalStock()),
                            "单位", stock.unitName()))));
            collector.addJump(new Jump("ITEM", stock.itemName(),
                    String.valueOf(stock.itemId()), null, null));
            stock.positions().stream().limit(n).forEach(p -> {
                collector.addJump(new Jump("LOT", p.lotNumber(),
                        String.valueOf(stock.itemId()), String.valueOf(p.lotId()), null));
                if (p.locationId() != null) {
                    collector.addJump(new Jump("LOCATION", p.locationPath(),
                            String.valueOf(stock.itemId()), String.valueOf(p.lotId()),
                            String.valueOf(p.locationId())));
                }
            });
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("itemName", stock.itemName());
            body.put("unitName", stock.unitName());
            body.put("totalStock", str(stock.totalStock()));
            body.put("positions", stock.positions().stream().limit(n).map(p -> Map.of(
                    "locationPath", p.locationPath(),
                    "lotNumber", p.lotNumber(),
                    "quantity", str(p.quantity()),
                    "expiryDate", p.expiryDate() != null ? ISO_DATE.format(p.expiryDate()) : "")).toList());
            return body;
        } catch (RuntimeException ex) {
            return unavailable("item_stock");
        }
    }

    @Tool(description = "查询服务端已确认位置及其子位置中的当前库存，返回物品、批次、位置、数量与到期日")
    Map<String, Object> locationStock(
            @ToolParam(description = "物品名称关键字，未指定则返回该位置内全部物品，选填") String itemKeyword,
            @ToolParam(description = "最多返回多少条库存位，1-50，选填") Integer limit
    ) {
        int n = boundedLimit(limit);
        if (!collector.beginToolCall()) {
            return unavailableBody("location_stock");
        }
        try {
            if (!isLocationTarget()) {
                return unavailable("location_stock");
            }
            var stock = queries.locationStock(householdId, target.id(), itemKeyword, n);
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

    @Tool(description = "查询当前家庭在指定天数内到期的临期批次（含物品、批次号、到期日、剩余数量）")
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
            if (isLocationTarget() || isLotTarget() && targetItemId() == null) {
                return unavailable("expiring_lots");
            }
            var lots = queries.expiringLots(
                    householdId, days, n, targetItemId(), isLotTarget() ? target.id() : null);
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
                collector.addJump(new Jump("REMINDER", "查看临期提醒", null, null, null));
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
                collector.addJump(new Jump("REMINDER", "查看低库存提醒", null, null, null));
            }
            return Map.of("lowStock", items.stream().map(item -> Map.of(
                    "itemName", item.itemName(),
                    "currentTotal", str(item.currentTotal()),
                    "threshold", str(item.threshold()))).toList());
        } catch (RuntimeException ex) {
            return unavailable("low_stock");
        }
    }

    @Tool(description = "查询某物品最近发生的库存流水（类型、原因、操作人、时间），作为不可变事实依据")
    Map<String, Object> itemMovements(
            @ToolParam(description = "物品 id") String itemId,
            @ToolParam(description = "最多返回多少条，1-50，选填") Integer limit
    ) {
        int n = boundedLimit(limit);
        if (!collector.beginToolCall()) {
            return unavailableBody("item_movements");
        }
        try {
            UUID authorizedItemId = authorizedItemId(itemId);
            var movements = queries.itemMovements(
                    householdId,
                    authorizedItemId,
                    n,
                    isLotTarget() ? target.id() : null,
                    isLocationTarget() ? target.id() : null);
            List<Map<String, String>> rows = movements.stream()
                    .map(m -> cellMap("类型", m.type(),
                            "数量", str(m.quantity()),
                            "原因", orDash(m.reason()),
                            "操作人", orDash(m.operatorDisplayName()),
                            "时间", m.businessTime() != null ? m.businessTime().toString() : "-",
                            "从", orDash(m.fromLocationPath()),
                            "到", orDash(m.toLocationPath())))
                    .toList();
            String itemName = movements.isEmpty() && isItemTarget() ? target.label()
                    : movements.isEmpty() ? itemId : movements.getFirst().itemName();
            collector.addResult(new StructuredResult("MOVEMENTS", "「" + itemName + "」最近流水", rows));
            collector.addJump(new Jump("ITEM", itemName, authorizedItemId.toString(), null, null));
            if (!rows.isEmpty()) {
                collector.addJump(new Jump("MOVEMENT", "查看流水", itemId, null, null));
            }
            return Map.of("movements", movements.stream().map(m -> Map.of(
                    "type", m.type(),
                    "quantity", str(m.quantity()),
                    "reason", orDash(m.reason()),
                    "operator", orDash(m.operatorDisplayName()),
                    "businessTime", m.businessTime() != null ? m.businessTime().toString() : "",
                    "from", orDash(m.fromLocationPath()),
                    "to", orDash(m.toLocationPath()))).toList());
        } catch (RuntimeException ex) {
            return unavailable("item_movements");
        }
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

    private HouseholdFactQueries.ItemStock scopeStock(HouseholdFactQueries.ItemStock stock) {
        if (target == null || isItemTarget()) return stock;
        var positions = stock.positions().stream()
                .filter(position -> !isLotTarget() || target.id().equals(position.lotId()))
                .filter(position -> !isLocationTarget() || target.id().equals(position.locationId()))
                .toList();
        BigDecimal total = positions.stream()
                .map(HouseholdFactQueries.Position::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new HouseholdFactQueries.ItemStock(
                stock.itemId(), stock.itemName(), stock.unitName(), total, positions);
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
