package com.zija.inventory.internal;

import com.zija.shared.ZijaAuditOutcome;
import com.zija.inventory.StockChangedEvent;
import com.zija.inventory.internal.event.InventoryEventPublisher;
import com.zija.inventory.internal.exception.InventoryMovementAlreadyReversedException;
import com.zija.inventory.internal.exception.InventoryReversalNotAllowedException;
import com.zija.inventory.internal.exception.InventoryReversalWouldNegativeException;
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
    private final IdempotencyService idempotencyService;

    public ReversalService(MovementMapper movementMapper,
                           StockPositionMapper stockPositionMapper,
                           SystemApi systemApi,
                           InventoryEventPublisher eventPublisher,
                           IdempotencyService idempotencyService) {
        this.movementMapper = movementMapper;
        this.stockPositionMapper = stockPositionMapper;
        this.systemApi = systemApi;
        this.eventPublisher = eventPublisher;
        this.idempotencyService = idempotencyService;
    }

    public record ReversalResult(UUID reversalMovementId, UUID lotId) {}

    /**
     * 冲正（撤销）一笔库存流水。
     * <p>
     * 创建一条 REVERSAL 补偿流水，不修改原始流水。事务编排顺序：
     * <ol>
     *   <li>幂等键检查（若有）：命中缓存则直接返回缓存的 {@code reversalMovementId}。
     *       这一步为携带相同 {@code Idempotency-Key} 的并发冲正提供串行化保证，
     *       避免两个并发请求都通过后续的 {@code countReversalOf} 检查而插入两条 REVERSAL、导致库存双重反转。</li>
     *   <li>加载原始流水，不存在则拒绝</li>
     *   <li>检查是否已被冲正（快速路径；无 key 并发调用的最终保证来自
     *       {@code inventory_movement.reversal_of} 唯一索引，见内联注释）</li>
     *   <li>类型检查：REVERSAL 类型不可冲正</li>
     *   <li>计算逆向影响并执行库存位增减（若导致负库存则全量回滚）</li>
     *   <li>插入 REVERSAL 流水</li>
     *   <li>记录幂等结果（若有 key）</li>
     *   <li>记录审计 + 发布事件</li>
     * </ol>
     * 原始流水不会被 UPDATE 或 DELETE。
     */
    @Transactional
    public ReversalResult reverse(UUID householdId, UUID accountId,
                                  UUID originalMovementId, String reason,
                                  String memo, String idempotencyKey) {
        String requestHash = idempotencyKey != null
                ? RequestHashing.sha256("REVERSAL:" + originalMovementId + ":" + reason + ":" + memo)
                : null;

        // 1. Idempotency replay: same Idempotency-Key + same request → return cached reversalMovementId.
        //    Placed before selectById so replay skips the movement lookup + countReversalOf round-trips.
        if (idempotencyKey != null) {
            var cached = idempotencyService.lockOrFind(householdId, idempotencyKey, requestHash);
            if (cached.isPresent()) {
                var payload = cached.get().getResponsePayload();
                UUID cachedReversalMovementId = UUID.fromString(payload.get("reversalMovementId").toString());
                UUID cachedLotId = UUID.fromString(payload.get("lotId").toString());
                return new ReversalResult(cachedReversalMovementId, cachedLotId);
            }
        }

        // 2. Load original movement
        MovementEntity original = movementMapper.selectById(originalMovementId);
        if (original == null || !original.getHouseholdId().equals(householdId)) {
            throw new InventoryReversalNotAllowedException("movement not found: " + originalMovementId);
        }

        // 3. Check if already reversed (fast path).
        //    countReversalOf is a non-locking SELECT COUNT(*), so concurrent calls
        //    WITHOUT an Idempotency-Key can both see count=0 here (TOCTOU). The hard
        //    guarantee is the UNIQUE index on inventory_movement.reversal_of (V9):
        //    the second concurrent INSERT fails with a unique violation and rolls
        //    back, so no application-level lock is needed on this path.
        if (movementMapper.countReversalOf(householdId, originalMovementId) > 0) {
            throw new InventoryMovementAlreadyReversedException(
                    "movement already reversed: " + originalMovementId);
        }

        // 4. REVERSAL type cannot be reversed
        if (MovementType.REVERSAL.equals(original.getType())) {
            throw new InventoryReversalNotAllowedException(
                    "REVERSAL movement cannot be reversed: " + originalMovementId);
        }

        UUID lotId = original.getLotId();
        BigDecimal originalQty = original.getQuantity();
        String type = original.getType();

        // 5. Calculate reverse impact and adjust stock positions
        //    For each subtractIfSufficient that returns 0 → throw, full rollback
        switch (type) {
            case MovementType.INBOUND -> subtractForReversal(householdId, lotId, original.getToLocationId(), originalQty);
            case MovementType.CONSUME, MovementType.LOSS -> addForReversal(householdId, lotId, original.getFromLocationId(), originalQty);
            case MovementType.TRANSFER -> {
                // Original moved stock from fromLocation to toLocation; reverse both endpoints
                subtractForReversal(householdId, lotId, original.getToLocationId(), originalQty);
                addForReversal(householdId, lotId, original.getFromLocationId(), originalQty);
            }
            case MovementType.ADJUSTMENT -> {
                if (original.getFromLocationId() == null && original.getToLocationId() == null) {
                    throw new InventoryReversalNotAllowedException(
                            "ADJUSTMENT movement has no location endpoints: " + originalMovementId);
                }
                if (original.getToLocationId() != null) {
                    subtractForReversal(householdId, lotId, original.getToLocationId(), originalQty);
                }
                if (original.getFromLocationId() != null) {
                    addForReversal(householdId, lotId, original.getFromLocationId(), originalQty);
                }
            }
            default -> throw new InventoryReversalNotAllowedException(
                    "unsupported movement type for reversal: " + type);
        }

        // 6. Insert REVERSAL movement
        //    Quantity = +originalQuantity (always positive)
        //    Endpoints express reverse of original
        UUID reversalMovementId = UUID.randomUUID();
        var reversal = new MovementEntity();
        reversal.setId(reversalMovementId);
        reversal.setHouseholdId(householdId);
        reversal.setLotId(lotId);
        reversal.setItemId(original.getItemId());
        reversal.setType(MovementType.REVERSAL);
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
            case MovementType.INBOUND -> {
                // Original: to=location. Reversal: from=location (stock leaving)
                reversal.setFromLocationId(original.getToLocationId());
                reversal.setToLocationId(null);
            }
            case MovementType.CONSUME, MovementType.LOSS -> {
                // Original: from=location. Reversal: to=location (stock returning)
                reversal.setFromLocationId(null);
                reversal.setToLocationId(original.getFromLocationId());
            }
            case MovementType.TRANSFER -> {
                // Original: from=A, to=B. Reversal: from=B, to=A
                reversal.setFromLocationId(original.getToLocationId());
                reversal.setToLocationId(original.getFromLocationId());
            }
            case MovementType.ADJUSTMENT -> {
                // Reverse the endpoints
                reversal.setFromLocationId(original.getToLocationId());
                reversal.setToLocationId(original.getFromLocationId());
            }
        }

        movementMapper.insert(reversal);

        // 6. Record idempotency success (within the same transaction as the REVERSAL insert,
        //    so a thrown exception here rolls back the reversal — Propagation.MANDATORY).
        if (idempotencyKey != null) {
            idempotencyService.recordSuccess(householdId, idempotencyKey, requestHash,
                    reversalMovementId,
                    Map.of("reversalMovementId", reversalMovementId, "lotId", lotId));
        }

        // 7. Audit
        systemApi.recordAudit(new SystemApi.AuditEvent(
                SystemApi.AuditAction.INVENTORY_REVERSAL, ZijaAuditOutcome.SUCCESS,
                householdId, accountId, null, null, null,
                Map.of("reversalOf", originalMovementId, "lotId", lotId,
                        "type", type, "quantity", originalQty)));

        // 8. Publish event
        eventPublisher.publish(new StockChangedEvent(
                UUID.randomUUID(), householdId, lotId, original.getItemId(),
                MovementType.REVERSAL, originalQty,
                reversal.getFromLocationId(), reversal.getToLocationId(),
                OffsetDateTime.now(), reversalMovementId,
                idempotencyKey != null ? UUID.fromString(idempotencyKey) : UUID.randomUUID(),
                accountId, null, originalMovementId));

        return new ReversalResult(reversalMovementId, lotId);
    }

    /**
     * 冲正时从指定位置扣减库存；位置不存在或数量不足则抛异常。
     */
    private void subtractForReversal(UUID householdId, UUID lotId, UUID locationId, BigDecimal qty) {
        StockPositionEntity sp = stockPositionMapper.lockOne(householdId, lotId, locationId);
        if (sp == null) {
            throw new InventoryReversalWouldNegativeException(
                    "stock position not found for reversal: lot=" + lotId + " location=" + locationId);
        }
        int updated = stockPositionMapper.subtractIfSufficient(householdId, lotId, locationId, qty);
        if (updated == 0) {
            throw new InventoryReversalWouldNegativeException(
                    "reversal would cause negative stock: lot=" + lotId + " location=" + locationId);
        }
    }

    /**
     * 冲正时向指定位置加回库存；位置不存在则先创建（防御性）。
     */
    private void addForReversal(UUID householdId, UUID lotId, UUID locationId, BigDecimal qty) {
        StockPositions.lockOrCreate(stockPositionMapper, householdId, lotId, locationId);
        stockPositionMapper.addQuantity(householdId, lotId, locationId, qty);
    }
}
