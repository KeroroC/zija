package com.zija.inventory.internal;

import com.zija.inventory.StockChangedEvent;
import com.zija.inventory.internal.event.InventoryEventPublisher;
import com.zija.inventory.internal.persistence.MovementEntity;
import com.zija.inventory.internal.persistence.MovementMapper;
import com.zija.inventory.internal.persistence.StockPositionEntity;
import com.zija.inventory.internal.persistence.StockPositionMapper;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class ReversalService {

    private final MovementMapper movementMapper;
    private final StockPositionMapper stockPositionMapper;
    private final SystemApi systemApi;
    private final InventoryEventPublisher eventPublisher;

    public ReversalService(MovementMapper movementMapper,
                           StockPositionMapper stockPositionMapper,
                           SystemApi systemApi,
                           InventoryEventPublisher eventPublisher) {
        this.movementMapper = movementMapper;
        this.stockPositionMapper = stockPositionMapper;
        this.systemApi = systemApi;
        this.eventPublisher = eventPublisher;
    }

    public record ReversalResult(UUID reversalMovementId, UUID lotId) {}

    /**
     * 冲正（撤销）一笔库存流水。
     * <p>
     * 创建一条 REVERSAL 补偿流水，不修改原始流水。事务编排顺序：
     * <ol>
     *   <li>加载原始流水，不存在则拒绝</li>
     *   <li>检查是否已被冲正，已冲正则拒绝</li>
     *   <li>类型检查：REVERSAL 类型不可冲正</li>
     *   <li>计算逆向影响并执行库存位增减（若导致负库存则全量回滚）</li>
     *   <li>插入 REVERSAL 流水</li>
     *   <li>记录审计 + 发布事件</li>
     * </ol>
     * 原始流水不会被 UPDATE 或 DELETE。
     */
    @Transactional
    public ReversalResult reverse(UUID householdId, UUID accountId,
                                  UUID originalMovementId, String reason,
                                  String memo, String idempotencyKey) {
        // 1. Load original movement
        MovementEntity original = movementMapper.selectById(originalMovementId);
        if (original == null || !original.getHouseholdId().equals(householdId)) {
            throw new InventoryReversalNotAllowedException("movement not found: " + originalMovementId);
        }

        // 2. Check if already reversed
        if (movementMapper.countReversalOf(householdId, originalMovementId) > 0) {
            throw new InventoryMovementAlreadyReversedException(
                    "movement already reversed: " + originalMovementId);
        }

        // 3. REVERSAL type cannot be reversed
        if ("REVERSAL".equals(original.getType())) {
            throw new InventoryReversalNotAllowedException(
                    "REVERSAL movement cannot be reversed: " + originalMovementId);
        }

        UUID lotId = original.getLotId();
        BigDecimal originalQty = original.getQuantity();
        String type = original.getType();

        // 4. Calculate reverse impact and adjust stock positions
        //    For each subtractIfSufficient that returns 0 → throw, full rollback
        switch (type) {
            case "INBOUND" -> {
                // Original added stock to toLocation → reverse subtract from toLocation
                UUID toLoc = original.getToLocationId();
                StockPositionEntity sp = stockPositionMapper.lockOne(householdId, lotId, toLoc);
                if (sp == null) {
                    throw new InventoryReversalWouldNegativeException(
                            "stock position not found for reversal: lot=" + lotId + " location=" + toLoc);
                }
                int updated = stockPositionMapper.subtractIfSufficient(householdId, lotId, toLoc, originalQty);
                if (updated == 0) {
                    throw new InventoryReversalWouldNegativeException(
                            "reversal would cause negative stock: lot=" + lotId + " location=" + toLoc);
                }
            }
            case "CONSUME", "LOSS" -> {
                // Original subtracted stock from fromLocation → reverse add back to fromLocation
                UUID fromLoc = original.getFromLocationId();
                StockPositionEntity sp = stockPositionMapper.lockOne(householdId, lotId, fromLoc);
                if (sp == null) {
                    // Create stock position if it doesn't exist (defensive)
                    sp = new StockPositionEntity();
                    sp.setId(UUID.randomUUID());
                    sp.setHouseholdId(householdId);
                    sp.setLotId(lotId);
                    sp.setLocationId(fromLoc);
                    sp.setQuantity(BigDecimal.ZERO);
                    sp.setRevision(0L);
                    sp.setCreatedAt(OffsetDateTime.now());
                    sp.setUpdatedAt(OffsetDateTime.now());
                    stockPositionMapper.insert(sp);
                }
                stockPositionMapper.addQuantity(householdId, lotId, fromLoc, originalQty);
            }
            case "TRANSFER" -> {
                // Original moved stock from fromLocation to toLocation
                // Reverse: subtract from toLocation, add back to fromLocation
                UUID fromLoc = original.getFromLocationId();
                UUID toLoc = original.getToLocationId();

                // Subtract from toLocation
                StockPositionEntity toSp = stockPositionMapper.lockOne(householdId, lotId, toLoc);
                if (toSp == null) {
                    throw new InventoryReversalWouldNegativeException(
                            "stock position not found for reversal: lot=" + lotId + " location=" + toLoc);
                }
                int updated = stockPositionMapper.subtractIfSufficient(householdId, lotId, toLoc, originalQty);
                if (updated == 0) {
                    throw new InventoryReversalWouldNegativeException(
                            "reversal would cause negative stock: lot=" + lotId + " location=" + toLoc);
                }

                // Add back to fromLocation
                StockPositionEntity fromSp = stockPositionMapper.lockOne(householdId, lotId, fromLoc);
                if (fromSp == null) {
                    fromSp = new StockPositionEntity();
                    fromSp.setId(UUID.randomUUID());
                    fromSp.setHouseholdId(householdId);
                    fromSp.setLotId(lotId);
                    fromSp.setLocationId(fromLoc);
                    fromSp.setQuantity(BigDecimal.ZERO);
                    fromSp.setRevision(0L);
                    fromSp.setCreatedAt(OffsetDateTime.now());
                    fromSp.setUpdatedAt(OffsetDateTime.now());
                    stockPositionMapper.insert(fromSp);
                }
                stockPositionMapper.addQuantity(householdId, lotId, fromLoc, originalQty);
            }
            case "ADJUSTMENT" -> {
                // ADJUSTMENT has both from and to locations
                // Determine direction by checking which location has stock
                UUID fromLoc = original.getFromLocationId();
                UUID toLoc = original.getToLocationId();

                if (fromLoc == null && toLoc == null) {
                    throw new InventoryReversalNotAllowedException(
                            "ADJUSTMENT movement has no location endpoints: " + originalMovementId);
                }

                // Reverse: subtract from toLocation (if exists), add back to fromLocation (if exists)
                if (toLoc != null) {
                    StockPositionEntity toSp = stockPositionMapper.lockOne(householdId, lotId, toLoc);
                    if (toSp == null) {
                        throw new InventoryReversalWouldNegativeException(
                                "stock position not found for reversal: lot=" + lotId + " location=" + toLoc);
                    }
                    int updated = stockPositionMapper.subtractIfSufficient(householdId, lotId, toLoc, originalQty);
                    if (updated == 0) {
                        throw new InventoryReversalWouldNegativeException(
                                "reversal would cause negative stock: lot=" + lotId + " location=" + toLoc);
                    }
                }
                if (fromLoc != null) {
                    StockPositionEntity fromSp = stockPositionMapper.lockOne(householdId, lotId, fromLoc);
                    if (fromSp == null) {
                        fromSp = new StockPositionEntity();
                        fromSp.setId(UUID.randomUUID());
                        fromSp.setHouseholdId(householdId);
                        fromSp.setLotId(lotId);
                        fromSp.setLocationId(fromLoc);
                        fromSp.setQuantity(BigDecimal.ZERO);
                        fromSp.setRevision(0L);
                        fromSp.setCreatedAt(OffsetDateTime.now());
                        fromSp.setUpdatedAt(OffsetDateTime.now());
                        stockPositionMapper.insert(fromSp);
                    }
                    stockPositionMapper.addQuantity(householdId, lotId, fromLoc, originalQty);
                }
            }
            default -> throw new InventoryReversalNotAllowedException(
                    "unsupported movement type for reversal: " + type);
        }

        // 5. Insert REVERSAL movement
        //    Quantity = +originalQuantity (always positive)
        //    Endpoints express reverse of original
        UUID reversalMovementId = UUID.randomUUID();
        var reversal = new MovementEntity();
        reversal.setId(reversalMovementId);
        reversal.setHouseholdId(householdId);
        reversal.setLotId(lotId);
        reversal.setItemId(original.getItemId());
        reversal.setType("REVERSAL");
        reversal.setQuantity(originalQty);
        reversal.setReason(reason);
        reversal.setMemo(memo);
        reversal.setOperatorAccountId(accountId);
        reversal.setBusinessTime(OffsetDateTime.now());
        reversal.setCreatedAt(OffsetDateTime.now());
        reversal.setIdempotencyKey(idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString());
        reversal.setReversalOf(originalMovementId);

        // Set reverse endpoints based on original type
        switch (type) {
            case "INBOUND" -> {
                // Original: to=location. Reversal: from=location (stock leaving)
                reversal.setFromLocationId(original.getToLocationId());
                reversal.setToLocationId(null);
            }
            case "CONSUME", "LOSS" -> {
                // Original: from=location. Reversal: to=location (stock returning)
                reversal.setFromLocationId(null);
                reversal.setToLocationId(original.getFromLocationId());
            }
            case "TRANSFER" -> {
                // Original: from=A, to=B. Reversal: from=B, to=A
                reversal.setFromLocationId(original.getToLocationId());
                reversal.setToLocationId(original.getFromLocationId());
            }
            case "ADJUSTMENT" -> {
                // Reverse the endpoints
                reversal.setFromLocationId(original.getToLocationId());
                reversal.setToLocationId(original.getFromLocationId());
            }
        }

        movementMapper.insert(reversal);

        // 6. Audit
        systemApi.recordAudit(new SystemApi.AuditEvent(
                "INVENTORY_REVERSAL", "SUCCESS",
                householdId, accountId, null, null, null,
                Map.of("reversalOf", originalMovementId, "lotId", lotId,
                        "type", type, "quantity", originalQty)));

        // 7. Publish event
        eventPublisher.publish(new StockChangedEvent(
                UUID.randomUUID(), householdId, lotId, original.getItemId(),
                "REVERSAL", originalQty,
                reversal.getFromLocationId(), reversal.getToLocationId(),
                OffsetDateTime.now(), reversalMovementId,
                idempotencyKey != null ? UUID.fromString(idempotencyKey) : UUID.randomUUID(),
                accountId, null, originalMovementId));

        return new ReversalResult(reversalMovementId, lotId);
    }
}
