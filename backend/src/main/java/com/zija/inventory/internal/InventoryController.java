package com.zija.inventory.internal;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zija.ZijaPrincipal;
import com.zija.household.HouseholdApi;
import com.zija.household.RequireMember;
import com.zija.inventory.InventoryApi;
import com.zija.inventory.internal.exception.InventoryLotNotFoundException;
import com.zija.inventory.internal.exception.StocktakeNotDraftException;
import com.zija.inventory.internal.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 库存 REST 控制器，提供库存位、批次、流水的只读查询以及入库、领用、报损、移位、冲正等命令端点。
 *
 * <p>所有端点均要求当前用户为家庭的活跃成员（{@link RequireMember}）。
 * 冲正与一致性检查额外要求管理员角色。</p>
 *
 * <p>端点概览：</p>
 * <ul>
 *   <li>{@code GET    /api/v1/inventory/stock-positions}               — 分页查询库存位</li>
 *   <li>{@code GET    /api/v1/inventory/lots}                          — 分页查询批次</li>
 *   <li>{@code GET    /api/v1/inventory/lots/{lotId}}                  — 批次详情</li>
 *   <li>{@code GET    /api/v1/inventory/movements}                     — 分页查询流水</li>
 *   <li>{@code GET    /api/v1/inventory/consistency-report}            — 一致性检查（管理员）</li>
 *   <li>{@code POST   /api/v1/inventory/lots}                          — 新建批次入库</li>
 *   <li>{@code POST   /api/v1/inventory/inbound}                       — 现有批次入库</li>
 *   <li>{@code POST   /api/v1/inventory/consume}                       — 领用（消耗库存）</li>
 *   <li>{@code POST   /api/v1/inventory/loss}                          — 报损</li>
 *   <li>{@code POST   /api/v1/inventory/transfer}                      — 移位（库存转移）</li>
 *   <li>{@code POST   /api/v1/inventory/movements/{id}/reverse}        — 冲正（管理员）</li>
 *   <li>{@code PUT    /api/v1/inventory/lots/{id}}                     — 更新批次元数据</li>
 *   <li>{@code POST   /api/v1/inventory/stocktakes}                    — 创建盘点草稿</li>
 *   <li>{@code PUT    /api/v1/inventory/stocktakes/{id}}               — 更新盘点草稿</li>
 *   <li>{@code PUT    /api/v1/inventory/stocktakes/{id}/refresh}       — 刷新盘点草稿快照</li>
 *   <li>{@code POST   /api/v1/inventory/stocktakes/{id}/confirm}       — 确认盘点</li>
 *   <li>{@code POST   /api/v1/inventory/stocktakes/{id}/cancel}        — 取消盘点</li>
 *   <li>{@code GET    /api/v1/inventory/stocktakes}                    — 分页查询盘点单</li>
 *   <li>{@code GET    /api/v1/inventory/stocktakes/{id}}               — 盘点单详情</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/inventory")
class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryApi inventoryApi;
    private final HouseholdApi householdApi;
    private final StockPositionMapper stockPositionMapper;
    private final LotMapper lotMapper;
    private final MovementMapper movementMapper;
    private final StocktakeMapper stocktakeMapper;

    InventoryController(InventoryService inventoryService,
                        InventoryApi inventoryApi,
                        HouseholdApi householdApi,
                        StockPositionMapper stockPositionMapper,
                        LotMapper lotMapper,
                        MovementMapper movementMapper,
                        StocktakeMapper stocktakeMapper) {
        this.inventoryService = inventoryService;
        this.inventoryApi = inventoryApi;
        this.householdApi = householdApi;
        this.stockPositionMapper = stockPositionMapper;
        this.lotMapper = lotMapper;
        this.movementMapper = movementMapper;
        this.stocktakeMapper = stocktakeMapper;
    }

    // ==================== Read-only endpoints ====================

    /**
     * 分页查询库存位。
     */
    @RequireMember
    @GetMapping("/stock-positions")
    Map<String, Object> listStockPositions(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        if (pageSize > 100) pageSize = 100;
        if (pageSize < 1) pageSize = 20;
        if (page < 1) page = 1;

        var pageObj = new Page<StockPositionWithDetails>(page, pageSize);
        var result = stockPositionMapper.findPage(pageObj, member.householdId(), itemId, locationId, "sp.updated_at DESC");

        return pagedResponse(
                result.getRecords().stream().map(this::toStockPositionResponse).toList(),
                result.getTotal(), page, pageSize);
    }

    /**
     * 分页查询批次。
     */
    @RequireMember
    @GetMapping("/lots")
    Map<String, Object> listLots(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        if (pageSize > 100) pageSize = 100;
        if (pageSize < 1) pageSize = 20;
        if (page < 1) page = 1;

        var pageObj = new Page<com.zija.inventory.internal.persistence.LotWithDetails>(page, pageSize);
        var result = lotMapper.findPage(pageObj, member.householdId(), itemId);

        return pagedResponse(
                result.getRecords().stream().map(this::toLotSummaryResponse).toList(),
                result.getTotal(), page, pageSize);
    }

    /**
     * 查询单个批次详情。
     */
    @RequireMember
    @GetMapping("/lots/{lotId}")
    Map<String, Object> getLot(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID lotId
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var lot = lotMapper.selectById(lotId);
        if (lot == null || !lot.getHouseholdId().equals(member.householdId())) {
            throw new InventoryLotNotFoundException();
        }
        return toLotResponse(lot);
    }

    /**
     * 分页查询库存流水。
     */
    @RequireMember
    @GetMapping("/movements")
    Map<String, Object> listMovements(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(required = false) UUID lotId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        if (pageSize > 100) pageSize = 100;
        if (pageSize < 1) pageSize = 20;
        if (page < 1) page = 1;

        OffsetDateTime fromDt = from != null ? from.atStartOfDay().atOffset(java.time.ZoneOffset.UTC) : null;
        OffsetDateTime toDt = to != null ? to.plusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.UTC) : null;

        // If lotId is specified, use the simple findByLot; otherwise use paged query
        if (lotId != null) {
            var movements = inventoryApi.movementsOfLot(member.householdId(), lotId);
            var response = new LinkedHashMap<String, Object>();
            response.put("items", movements.stream().map(this::toMovementResponse).toList());
            response.put("total", (long) movements.size());
            response.put("page", 1);
            response.put("pageSize", movements.size());
            return response;
        }

        var pageObj = new Page<com.zija.inventory.internal.persistence.MovementEntity>(page, pageSize);
        var result = movementMapper.findPage(pageObj, member.householdId(), type, itemId, locationId, null, fromDt, toDt, "created_at DESC");

        return pagedResponse(
                result.getRecords().stream().map(this::toMovementEntityResponse).toList(),
                result.getTotal(), page, pageSize);
    }

    /**
     * 一致性检查（仅管理员）。
     */
    @RequireMember
    @GetMapping("/consistency-report")
    Map<String, Object> consistencyReport(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(required = false) UUID itemId
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var discrepancies = inventoryService.checkConsistency(
                principal.getAccountId(), member.householdId(), itemId);

        var response = new LinkedHashMap<String, Object>();
        response.put("discrepancies", discrepancies.stream().map(d -> {
            var m = new LinkedHashMap<String, Object>();
            m.put("lotId", d.lotId());
            m.put("locationId", d.locationId());
            m.put("expected", d.expected());
            m.put("actual", d.actual());
            return m;
        }).toList());
        response.put("total", discrepancies.size());
        return response;
    }

    // ==================== Write endpoints ====================

    /**
     * 新建批次入库。
     */
    @RequireMember
    @PostMapping("/lots")
    Map<String, Object> inboundNewLot(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody InboundNewLotRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var cmd = new StockCommandService.InboundNewLotCommand(
                request.itemId(), request.quantity(),
                request.purchaseDate(), request.productionDate(), request.expiryDate(),
                request.serialNumber(), request.memo(),
                idempotencyKey);
        var result = inventoryService.inboundNewLot(
                principal.getAccountId(), member.householdId(), request.locationId(), cmd);
        return toInboundResponse(result);
    }

    /**
     * 现有批次入库。
     */
    @RequireMember
    @PostMapping("/inbound")
    Map<String, Object> inboundExistingLot(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody InboundExistingLotRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var result = inventoryService.inboundExistingLot(
                principal.getAccountId(), member.householdId(),
                request.locationId(), request.lotId(),
                request.quantity(), request.memo(), idempotencyKey);
        return toInboundResponse(result);
    }

    /**
     * 领用（消耗库存）。
     */
    @RequireMember
    @PostMapping("/consume")
    Map<String, Object> consume(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody ConsumeRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var result = inventoryService.consume(
                principal.getAccountId(), member.householdId(),
                request.lotId(), request.locationId(),
                request.quantity(), request.reason(), request.memo(), idempotencyKey);
        return toInboundResponse(result);
    }

    /**
     * 报损（报废/过期等损耗）。
     */
    @RequireMember
    @PostMapping("/loss")
    Map<String, Object> loss(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody LossRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var result = inventoryService.loss(
                principal.getAccountId(), member.householdId(),
                request.lotId(), request.locationId(),
                request.quantity(), request.reason(), request.memo(), idempotencyKey);
        return toInboundResponse(result);
    }

    /**
     * 移位（库存转移）。
     */
    @RequireMember
    @PostMapping("/transfer")
    Map<String, Object> transfer(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var result = inventoryService.transfer(
                principal.getAccountId(), member.householdId(),
                request.lotId(), request.fromLocationId(), request.toLocationId(),
                request.quantity(), request.memo(), idempotencyKey);
        return toInboundResponse(result);
    }

    /**
     * 冲正（撤销）一笔库存流水。仅管理员可执行。
     */
    @RequireMember
    @PostMapping("/movements/{id}/reverse")
    Map<String, Object> reverse(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody ReverseRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var result = inventoryService.reverse(
                principal.getAccountId(), member.householdId(),
                id, request.reason(), request.memo(), idempotencyKey);
        var response = new LinkedHashMap<String, Object>();
        response.put("reversalMovementId", result.reversalMovementId());
        response.put("lotId", result.lotId());
        return response;
    }

    /**
     * 更新批次元数据。
     */
    @RequireMember
    @PutMapping("/lots/{id}")
    Map<String, Object> updateLotMeta(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLotMetaRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var lot = inventoryService.updateLotMeta(
                principal.getAccountId(), member.householdId(), id,
                request.version(),
                request.purchaseDate(), request.productionDate(), request.expiryDate(),
                request.serialNumber(), request.memo());
        return toLotResponse(lot);
    }

    // ==================== Stocktake endpoints ====================

    /**
     * 创建盘点草稿。
     */
    @RequireMember
    @PostMapping("/stocktakes")
    Map<String, Object> createStocktake(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody CreateStocktakeRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        UUID id = inventoryService.createStocktakeDraft(
                principal.getAccountId(), member.householdId(), request.locationId());
        var response = new LinkedHashMap<String, Object>();
        response.put("id", id);
        return response;
    }

    /**
     * 更新盘点草稿行项。
     */
    @RequireMember
    @PutMapping("/stocktakes/{id}")
    Map<String, Object> updateStocktake(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStocktakeRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var updates = request.updates().stream()
                .map(u -> new StocktakeService.StocktakeItemUpdate(
                        u.lotId(), u.locationId(), u.actualQuantity(), u.reason()))
                .toList();
        inventoryService.updateStocktakeDraft(
                principal.getAccountId(), member.householdId(), id, request.version(), updates);
        var response = new LinkedHashMap<String, Object>();
        response.put("status", "ok");
        return response;
    }

    /**
     * 刷新盘点草稿快照。
     */
    @RequireMember
    @PutMapping("/stocktakes/{id}/refresh")
    Map<String, Object> refreshStocktake(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody RefreshStocktakeRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        inventoryService.refreshStocktakeDraft(
                principal.getAccountId(), member.householdId(), id, request.version(), request.locationId());
        var response = new LinkedHashMap<String, Object>();
        response.put("status", "ok");
        return response;
    }

    /**
     * 确认盘点。
     */
    @RequireMember
    @PostMapping("/stocktakes/{id}/confirm")
    Map<String, Object> confirmStocktake(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionOnlyRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var result = inventoryService.confirmStocktake(
                principal.getAccountId(), member.householdId(), id, request.version());
        var response = new LinkedHashMap<String, Object>();
        response.put("stocktakeId", result.stocktakeId());
        response.put("adjustedCount", result.adjustedCount());
        return response;
    }

    /**
     * 取消盘点。
     */
    @RequireMember
    @PostMapping("/stocktakes/{id}/cancel")
    Map<String, Object> cancelStocktake(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionOnlyRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        inventoryService.cancelStocktake(
                principal.getAccountId(), member.householdId(), id, request.version());
        var response = new LinkedHashMap<String, Object>();
        response.put("status", "ok");
        return response;
    }

    /**
     * 分页查询盘点单。
     */
    @RequireMember
    @GetMapping("/stocktakes")
    Map<String, Object> listStocktakes(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        if (pageSize > 100) pageSize = 100;
        if (pageSize < 1) pageSize = 20;
        if (page < 1) page = 1;

        var pageObj = new Page<StocktakeEntity>(page, pageSize);
        var result = stocktakeMapper.findPage(pageObj, member.householdId(), status, "created_at DESC");

        return pagedResponse(
                result.getRecords().stream().map(this::toStocktakeResponse).toList(),
                result.getTotal(), page, pageSize);
    }

    /**
     * 查询盘点单详情（含行项）。
     */
    @RequireMember
    @GetMapping("/stocktakes/{id}")
    Map<String, Object> getStocktake(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var stocktake = stocktakeMapper.selectById(id);
        if (stocktake == null || !stocktake.getHouseholdId().equals(member.householdId())) {
            throw new StocktakeNotDraftException();
        }
        var response = toStocktakeResponse(stocktake);
        var items = inventoryService.stocktakeItems(
                principal.getAccountId(), member.householdId(), id);
        response.put("items", items.stream().map(this::toStocktakeItemResponse).toList());
        return response;
    }

    // ==================== Response helpers ====================

    private Map<String, Object> toInboundResponse(StockCommandService.InboundResult result) {
        var map = new LinkedHashMap<String, Object>();
        map.put("lotId", result.lotId());
        map.put("locationId", result.locationId());
        map.put("movementId", result.movementId());
        map.put("quantityAfter", result.quantityAfter());
        map.put("serialDuplicated", result.serialDuplicated());
        return map;
    }

    private Map<String, Object> toLotResponse(LotEntity lot) {
        var map = new LinkedHashMap<String, Object>();
        map.put("id", lot.getId());
        map.put("householdId", lot.getHouseholdId());
        map.put("itemId", lot.getItemId());
        map.put("purchaseDate", lot.getPurchaseDate());
        map.put("productionDate", lot.getProductionDate());
        map.put("expiryDate", lot.getExpiryDate());
        map.put("lotNumber", lot.getLotNumber());
        map.put("serialNumber", lot.getSerialNumber());
        map.put("memo", lot.getMemo());
        map.put("version", lot.getVersion());
        map.put("createdAt", lot.getCreatedAt());
        map.put("updatedAt", lot.getUpdatedAt());
        return map;
    }

    /**
     * 分页响应：统一 items/total/page/pageSize 结构。
     */
    private Map<String, Object> pagedResponse(List<?> items, long total, int page, int pageSize) {
        var response = new LinkedHashMap<String, Object>();
        response.put("items", items);
        response.put("total", total);
        response.put("page", page);
        response.put("pageSize", pageSize);
        return response;
    }

    /**
     * 库存位列表 DTO：SQL join 结果（{@link StockPositionWithDetails}）→ HTTP 响应。
     * 与 {@link #toLotResponse} 不同，本映射用于列表端点，避免直接序列化持久层 join 记录。
     */
    private Map<String, Object> toStockPositionResponse(StockPositionWithDetails sp) {
        var map = new LinkedHashMap<String, Object>();
        map.put("lotId", sp.lotId());
        map.put("locationId", sp.locationId());
        map.put("quantity", sp.quantity());
        map.put("revision", sp.revision());
        map.put("updatedAt", sp.updatedAt());
        map.put("itemName", sp.itemName());
        map.put("itemManagementType", sp.itemManagementType());
        map.put("unitName", sp.unitName());
        map.put("lotNumber", sp.lotNumber());
        map.put("serialNumber", sp.serialNumber());
        map.put("expiryDate", sp.expiryDate());
        return map;
    }

    /**
     * 批次列表 DTO：SQL join 结果（{@link LotWithDetails}）→ HTTP 响应。
     */
    private Map<String, Object> toLotSummaryResponse(LotWithDetails lot) {
        var map = new LinkedHashMap<String, Object>();
        map.put("lotId", lot.lotId());
        map.put("itemId", lot.itemId());
        map.put("itemName", lot.itemName());
        map.put("unitName", lot.unitName());
        map.put("totalQuantity", lot.totalQuantity());
        map.put("purchaseDate", lot.purchaseDate());
        map.put("productionDate", lot.productionDate());
        map.put("expiryDate", lot.expiryDate());
        map.put("lotNumber", lot.lotNumber());
        map.put("serialNumber", lot.serialNumber());
        map.put("memo", lot.memo());
        map.put("version", lot.version());
        map.put("createdAt", lot.createdAt());
        map.put("updatedAt", lot.updatedAt());
        return map;
    }

    /**
     * 流水列表 DTO：{@link MovementEntity} → HTTP 响应。剥离 householdId 等实体内部字段。
     */
    private Map<String, Object> toMovementEntityResponse(MovementEntity m) {
        var map = new LinkedHashMap<String, Object>();
        map.put("id", m.getId());
        map.put("lotId", m.getLotId());
        map.put("itemId", m.getItemId());
        map.put("type", m.getType());
        map.put("quantity", m.getQuantity());
        map.put("fromLocationId", m.getFromLocationId());
        map.put("toLocationId", m.getToLocationId());
        map.put("reason", m.getReason());
        map.put("memo", m.getMemo());
        map.put("operatorAccountId", m.getOperatorAccountId());
        map.put("businessTime", m.getBusinessTime());
        map.put("createdAt", m.getCreatedAt());
        map.put("idempotencyKey", m.getIdempotencyKey());
        map.put("reversalOf", m.getReversalOf());
        return map;
    }

    private Map<String, Object> toStocktakeResponse(StocktakeEntity st) {
        var map = new LinkedHashMap<String, Object>();
        map.put("id", st.getId());
        map.put("householdId", st.getHouseholdId());
        map.put("status", st.getStatus());
        map.put("createdBy", st.getCreatedBy());
        map.put("createdAt", st.getCreatedAt());
        map.put("updatedAt", st.getUpdatedAt());
        map.put("completedAt", st.getCompletedAt());
        map.put("version", st.getVersion());
        return map;
    }

    private Map<String, Object> toStocktakeItemResponse(StocktakeItemEntity item) {
        var map = new LinkedHashMap<String, Object>();
        map.put("id", item.getId());
        map.put("lotId", item.getLotId());
        map.put("locationId", item.getLocationId());
        map.put("bookQuantity", item.getBookQuantity());
        map.put("actualQuantity", item.getActualQuantity());
        map.put("positionRevision", item.getPositionRevision());
        map.put("reason", item.getReason());
        return map;
    }

    private Map<String, Object> toMovementResponse(InventoryApi.MovementInfo m) {
        var map = new LinkedHashMap<String, Object>();
        map.put("id", m.id());
        map.put("lotId", m.lotId());
        map.put("itemId", m.itemId());
        map.put("type", m.type());
        map.put("quantity", m.quantity());
        map.put("fromLocationId", m.fromLocationId());
        map.put("toLocationId", m.toLocationId());
        map.put("reason", m.reason());
        map.put("operatorAccountId", m.operatorAccountId());
        map.put("businessTime", m.businessTime());
        map.put("createdAt", m.createdAt());
        map.put("idempotencyKey", m.idempotencyKey());
        map.put("reversalOf", m.reversalOf());
        return map;
    }

    // ==================== Request DTOs ====================

    record InboundNewLotRequest(
            @NotNull UUID itemId,
            @NotNull @Positive BigDecimal quantity,
            @NotNull UUID locationId,
            LocalDate purchaseDate,
            LocalDate productionDate,
            LocalDate expiryDate,
            String serialNumber,
            String memo
    ) {}

    record InboundExistingLotRequest(
            @NotNull UUID lotId,
            @NotNull UUID locationId,
            @NotNull @Positive BigDecimal quantity,
            String memo
    ) {}

    record ConsumeRequest(
            @NotNull UUID lotId,
            @NotNull UUID locationId,
            @NotNull @Positive BigDecimal quantity,
            String reason,
            String memo
    ) {}

    record LossRequest(
            @NotNull UUID lotId,
            @NotNull UUID locationId,
            @NotNull @Positive BigDecimal quantity,
            @NotBlank String reason,
            String memo
    ) {}

    record TransferRequest(
            @NotNull UUID lotId,
            @NotNull UUID fromLocationId,
            @NotNull UUID toLocationId,
            @NotNull @Positive BigDecimal quantity,
            String memo
    ) {
        @AssertTrue(message = "fromLocationId and toLocationId must be different")
        private boolean isDifferentLocations() {
            return !fromLocationId.equals(toLocationId);
        }
    }

    record ReverseRequest(
            String reason,
            String memo
    ) {}

    record UpdateLotMetaRequest(
            @NotNull Integer version,
            LocalDate purchaseDate,
            LocalDate productionDate,
            LocalDate expiryDate,
            String serialNumber,
            String memo
    ) {}

    // ---- Stocktake request DTOs ----

    record CreateStocktakeRequest(
            @NotNull UUID locationId
    ) {}

    record UpdateStocktakeRequest(
            @NotNull Integer version,
            @NotNull List<StocktakeItemUpdateDto> updates
    ) {}

    record StocktakeItemUpdateDto(
            @NotNull UUID lotId,
            @NotNull UUID locationId,
            @NotNull @Positive BigDecimal actualQuantity,
            String reason
    ) {}

    record RefreshStocktakeRequest(
            @NotNull Integer version,
            @NotNull UUID locationId
    ) {}

    record VersionOnlyRequest(
            @NotNull Integer version
    ) {}
}
