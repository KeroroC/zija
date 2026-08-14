package com.zija.inventory.internal;

import com.zija.shared.ZijaAuditOutcome;
import com.zija.catalog.CatalogApi;
import com.zija.inventory.StockChangedEvent;
import com.zija.inventory.internal.event.InventoryEventPublisher;
import com.zija.inventory.internal.exception.InventoryArchivedItemException;
import com.zija.inventory.internal.exception.InventoryInsufficientStockException;
import com.zija.inventory.internal.exception.InventoryLotNotFoundException;
import com.zija.inventory.internal.persistence.LotMapper;
import com.zija.inventory.internal.persistence.MovementEntity;
import com.zija.inventory.internal.persistence.MovementMapper;
import com.zija.inventory.internal.persistence.StockPositionEntity;
import com.zija.inventory.internal.persistence.StockPositionMapper;
import com.zija.location.LocationApi;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class StockCommandService {

    private final LotService lotService;
    private final LotMapper lotMapper;
    private final CatalogApi catalogApi;
    private final LocationApi locationApi;
    private final StockPositionMapper stockPositionMapper;
    private final MovementMapper movementMapper;
    private final IdempotencyService idempotencyService;
    private final SystemApi systemApi;
    private final InventoryEventPublisher eventPublisher;

    public StockCommandService(LotService lotService, LotMapper lotMapper,
                               CatalogApi catalogApi, LocationApi locationApi,
                               StockPositionMapper stockPositionMapper,
                               MovementMapper movementMapper, IdempotencyService idempotencyService,
                               SystemApi systemApi, InventoryEventPublisher eventPublisher) {
        this.lotService = lotService;
        this.lotMapper = lotMapper;
        this.catalogApi = catalogApi;
        this.locationApi = locationApi;
        this.stockPositionMapper = stockPositionMapper;
        this.movementMapper = movementMapper;
        this.idempotencyService = idempotencyService;
        this.systemApi = systemApi;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 新建批次入库。
     * <p>
     * 事务编排顺序：校验精度 → 创建批次 → 校验位置并标记引用 → 锁定/创建库存位 → 增加数量 → 插入流水 → 记录幂等 → 审计 → 发布事件。
     */
    @Transactional
    public InboundResult inboundNewLot(UUID householdId, UUID accountId,
                                       UUID locationId, InboundNewLotCommand cmd) {
        // 1. Validate quantity precision (need unit's decimal scale)
        CatalogApi.ItemInfo itemInfo = requireActiveItemOrThrow(householdId, cmd.itemId());
        var unitInfo = catalogApi.requireUnit(householdId, itemInfo.unitId());
        BigDecimal validatedQty = QuantityPrecision.require(unitInfo.decimalScale(), cmd.quantity());

        // 1.5. Check idempotency replay
        String requestHash = null;
        if (cmd.idempotencyKey() != null) {
            requestHash = RequestHashing.sha256("INBOUND_NEW:"
                    + cmd.itemId() + ":" + locationId + ":"
                    + validatedQty.scale() + ":" + validatedQty.stripTrailingZeros() + ":"
                    + cmd.serialNumber() + ":" + cmd.expiryDate());
            var replayed = replayCached(householdId, cmd.idempotencyKey(), requestHash, locationId);
            if (replayed.isPresent()) return replayed.get();
        }

        // 2. Create new lot (validates item is active internally, lot number auto-generated)
        UUID lotId = lotService.createLot(householdId, cmd.itemId(),
                cmd.purchaseDate(), cmd.productionDate(), cmd.expiryDate(),
                cmd.serialNumber(), cmd.memo());

        // 3. Validate location and mark referenced
        locationApi.requireLocation(householdId, locationId);
        locationApi.markReferenced(householdId, locationId);

        // 4. Lock or create stock position
        StockPositionEntity sp = StockPositions.lockOrCreate(stockPositionMapper, householdId, lotId, locationId);

        // 5. Add quantity to stock position
        stockPositionMapper.addQuantity(householdId, lotId, locationId, validatedQty);

        // 6. Insert INBOUND movement
        UUID idempotencyKeyUuid = cmd.idempotencyKey() != null
                ? UUID.fromString(cmd.idempotencyKey())
                : UUID.randomUUID();
        String idempotencyKey = idempotencyKeyUuid.toString();
        UUID movementId = UUID.randomUUID();
        var movement = new MovementEntity();
        movement.setId(movementId);
        movement.setHouseholdId(householdId);
        movement.setLotId(lotId);
        movement.setItemId(cmd.itemId());
        movement.setType(MovementType.INBOUND);
        movement.setQuantity(validatedQty);
        movement.setFromLocationId(null);
        movement.setToLocationId(locationId);
        movement.setReason(null);
        movement.setMemo(cmd.memo());
        movement.setOperatorAccountId(accountId);
        movement.setBusinessTime(OffsetDateTime.now());
        movement.setCreatedAt(OffsetDateTime.now());
        movement.setIdempotencyKey(idempotencyKey);
        movement.setReversalOf(null);
        movementMapper.insert(movement);

        // 7. Record idempotency
        if (cmd.idempotencyKey() != null) {
            idempotencyService.recordSuccess(householdId, cmd.idempotencyKey(),
                    requestHash, movementId, Map.of("lotId", lotId, "movementId", movementId));
        }

        // 8. Audit
        systemApi.recordAudit(new SystemApi.AuditEvent(
                SystemApi.AuditAction.INVENTORY_INBOUND, ZijaAuditOutcome.SUCCESS,
                householdId, accountId, null, null, null,
                Map.of("lotId", lotId, "itemId", cmd.itemId(),
                        "locationId", locationId, "quantity", validatedQty)));

        // 9. Publish event
        eventPublisher.publish(new StockChangedEvent(
                UUID.randomUUID(), householdId, lotId, cmd.itemId(),
                MovementType.INBOUND, validatedQty, null, locationId,
                OffsetDateTime.now(), movementId, idempotencyKeyUuid,
                accountId, null, null));

        // 10. Return result
        boolean serialDuplicated = cmd.serialNumber() != null
                && lotService.serialNumberDuplicated(householdId, cmd.itemId(), cmd.serialNumber());
        return new InboundResult(lotId, locationId, movementId, validatedQty, serialDuplicated);
    }

    /**
     * 现有批次入库。
     * <p>
     * 事务编排顺序：锁定批次 → 校验物品活跃 → 校验精度 → 校验位置并标记引用 → 锁定/创建库存位 → 增加数量 → 插入流水 → 记录幂等 → 审计 → 发布事件。
     */
    @Transactional
    public InboundResult inboundExistingLot(UUID householdId, UUID accountId,
                                            UUID locationId, UUID lotId,
                                            BigDecimal quantity, String memo,
                                            String idempotencyKey) {
        // 1. Lock lot by UUID (stock position lockOne below provides the critical row-level lock)
        var lot = lotMapper.selectById(lotId);
        if (lot == null || !lot.getHouseholdId().equals(householdId)) {
            throw new InventoryLotNotFoundException();
        }
        UUID itemId = lot.getItemId();

        // 2. Validate item is ACTIVE
        CatalogApi.ItemInfo itemInfo = requireActiveItemOrThrow(householdId, itemId);

        // 3. Validate quantity precision
        var unitInfo = catalogApi.requireUnit(householdId, itemInfo.unitId());
        BigDecimal validatedQty = QuantityPrecision.require(unitInfo.decimalScale(), quantity);

        // 3.5. Check idempotency replay
        String requestHash = null;
        if (idempotencyKey != null) {
            requestHash = RequestHashing.sha256("INBOUND_EXISTING:"
                    + itemId + ":" + lotId + ":" + locationId + ":"
                    + validatedQty.scale() + ":" + validatedQty.stripTrailingZeros());
            var replayed = replayCached(householdId, idempotencyKey, requestHash, locationId);
            if (replayed.isPresent()) return replayed.get();
        }

        // 4. Validate location and mark referenced
        locationApi.requireLocation(householdId, locationId);
        locationApi.markReferenced(householdId, locationId);

        // 5. Lock or create stock position
        StockPositionEntity sp = StockPositions.lockOrCreate(stockPositionMapper, householdId, lotId, locationId);

        // 6. Add quantity to stock position
        stockPositionMapper.addQuantity(householdId, lotId, locationId, validatedQty);

        // 7. Insert INBOUND movement
        UUID idempotencyKeyUuid = idempotencyKey != null
                ? UUID.fromString(idempotencyKey)
                : UUID.randomUUID();
        String idempotencyKeyStr = idempotencyKeyUuid.toString();
        UUID movementId = UUID.randomUUID();
        var movement = new MovementEntity();
        movement.setId(movementId);
        movement.setHouseholdId(householdId);
        movement.setLotId(lotId);
        movement.setItemId(itemId);
        movement.setType(MovementType.INBOUND);
        movement.setQuantity(validatedQty);
        movement.setFromLocationId(null);
        movement.setToLocationId(locationId);
        movement.setReason(null);
        movement.setMemo(memo);
        movement.setOperatorAccountId(accountId);
        movement.setBusinessTime(OffsetDateTime.now());
        movement.setCreatedAt(OffsetDateTime.now());
        movement.setIdempotencyKey(idempotencyKeyStr);
        movement.setReversalOf(null);
        movementMapper.insert(movement);

        // 8. Record idempotency
        if (idempotencyKey != null) {
            idempotencyService.recordSuccess(householdId, idempotencyKey,
                    requestHash, movementId, Map.of("lotId", lotId, "movementId", movementId));
        }

        // 9. Audit
        systemApi.recordAudit(new SystemApi.AuditEvent(
                SystemApi.AuditAction.INVENTORY_INBOUND, ZijaAuditOutcome.SUCCESS,
                householdId, accountId, null, null, null,
                Map.of("lotId", lotId, "itemId", itemId,
                        "locationId", locationId, "quantity", validatedQty)));

        // 10. Publish event
        eventPublisher.publish(new StockChangedEvent(
                UUID.randomUUID(), householdId, lotId, itemId,
                MovementType.INBOUND, validatedQty, null, locationId,
                OffsetDateTime.now(), movementId, idempotencyKeyUuid,
                accountId, null, null));

        // 11. Return result
        return new InboundResult(lotId, locationId, movementId, validatedQty, false);
    }

    /**
     * 领用（消耗库存）。
     * <p>
     * 事务编排顺序：锁定批次 → 校验物品（允许归档物品） → 校验精度 → 校验位置 → 锁定库存位（不存在则不足） → 条件扣减（不足则拒绝） → 插入 CONSUME 流水 → 记录幂等 → 审计 → 发布事件。
     */
    @Transactional
    public InboundResult consume(UUID householdId, UUID accountId, UUID lotId,
                                 UUID locationId, BigDecimal quantity,
                                 String reason, String memo, String idempotencyKey) {
        var prepared = ExistingLotQuantity.require(lotMapper, catalogApi, householdId, lotId, quantity);
        UUID itemId = prepared.itemId();
        BigDecimal validatedQty = prepared.validatedQty();

        // Check idempotency replay
        String requestHash = null;
        if (idempotencyKey != null) {
            requestHash = RequestHashing.sha256("CONSUME:"
                    + itemId + ":" + lotId + ":" + locationId + ":"
                    + validatedQty.scale() + ":" + validatedQty.stripTrailingZeros());
            var replayed = replayCached(householdId, idempotencyKey, requestHash, locationId);
            if (replayed.isPresent()) return replayed.get();
        }

        // 4. Validate location
        locationApi.requireLocation(householdId, locationId);

        // 5-6. Lock stock position (must exist) and subtract if sufficient
        subtractOrThrow(householdId, lotId, locationId, validatedQty);

        // 7. Insert CONSUME movement
        UUID movementId = insertDeductionMovement(
                householdId, accountId, lotId, itemId, MovementType.CONSUME,
                validatedQty, locationId, reason, memo, idempotencyKey);
        UUID idempotencyKeyUuid = idempotencyKey != null ? UUID.fromString(idempotencyKey) : UUID.randomUUID();

        // 8. Record idempotency
        if (idempotencyKey != null) {
            idempotencyService.recordSuccess(householdId, idempotencyKey,
                    requestHash, movementId, Map.of("lotId", lotId, "movementId", movementId));
        }

        // 9. Audit
        systemApi.recordAudit(new SystemApi.AuditEvent(
                SystemApi.AuditAction.INVENTORY_CONSUME, ZijaAuditOutcome.SUCCESS,
                householdId, accountId, null, null, null,
                Map.of("lotId", lotId, "itemId", itemId,
                        "locationId", locationId, "quantity", validatedQty)));

        // 10. Publish event
        eventPublisher.publish(new StockChangedEvent(
                UUID.randomUUID(), householdId, lotId, itemId,
                MovementType.CONSUME, validatedQty, locationId, null,
                OffsetDateTime.now(), movementId, idempotencyKeyUuid,
                accountId, reason, null));

        // 11. Return result (quantity remaining after deduction)
        var updatedSp = stockPositionMapper.lockOne(householdId, lotId, locationId);
        BigDecimal quantityAfter = updatedSp != null ? updatedSp.getQuantity() : BigDecimal.ZERO;
        return new InboundResult(lotId, locationId, movementId, quantityAfter, false);
    }

    /**
     * 报损（报废/过期等损耗）。
     * <p>
     * 事务编排顺序：锁定批次 → 校验物品（允许归档物品） → 校验精度 → 校验位置 → 锁定库存位（不存在则不足） → 条件扣减（不足则拒绝） → 插入 LOSS 流水 → 记录幂等 → 审计 → 发布事件。
     */
    @Transactional
    public InboundResult loss(UUID householdId, UUID accountId, UUID lotId,
                              UUID locationId, BigDecimal quantity,
                              String reason, String memo, String idempotencyKey) {
        var prepared = ExistingLotQuantity.require(lotMapper, catalogApi, householdId, lotId, quantity);
        UUID itemId = prepared.itemId();
        BigDecimal validatedQty = prepared.validatedQty();

        // Check idempotency replay
        String requestHash = null;
        if (idempotencyKey != null) {
            requestHash = RequestHashing.sha256("LOSS:"
                    + itemId + ":" + lotId + ":" + locationId + ":"
                    + validatedQty.scale() + ":" + validatedQty.stripTrailingZeros());
            var replayed = replayCached(householdId, idempotencyKey, requestHash, locationId);
            if (replayed.isPresent()) return replayed.get();
        }

        // 4. Validate location
        locationApi.requireLocation(householdId, locationId);

        // 5-6. Lock stock position (must exist) and subtract if sufficient
        subtractOrThrow(householdId, lotId, locationId, validatedQty);

        // 7. Insert LOSS movement
        UUID movementId = insertDeductionMovement(
                householdId, accountId, lotId, itemId, MovementType.LOSS,
                validatedQty, locationId, reason, memo, idempotencyKey);
        UUID idempotencyKeyUuid = idempotencyKey != null ? UUID.fromString(idempotencyKey) : UUID.randomUUID();

        // 8. Record idempotency
        if (idempotencyKey != null) {
            idempotencyService.recordSuccess(householdId, idempotencyKey,
                    requestHash, movementId, Map.of("lotId", lotId, "movementId", movementId));
        }

        // 9. Audit
        systemApi.recordAudit(new SystemApi.AuditEvent(
                SystemApi.AuditAction.INVENTORY_LOSS, ZijaAuditOutcome.SUCCESS,
                householdId, accountId, null, null, null,
                Map.of("lotId", lotId, "itemId", itemId,
                        "locationId", locationId, "quantity", validatedQty)));

        // 10. Publish event
        eventPublisher.publish(new StockChangedEvent(
                UUID.randomUUID(), householdId, lotId, itemId,
                MovementType.LOSS, validatedQty, locationId, null,
                OffsetDateTime.now(), movementId, idempotencyKeyUuid,
                accountId, reason, null));

        // 11. Return result (quantity remaining after deduction)
        var updatedSp = stockPositionMapper.lockOne(householdId, lotId, locationId);
        BigDecimal quantityAfter = updatedSp != null ? updatedSp.getQuantity() : BigDecimal.ZERO;
        return new InboundResult(lotId, locationId, movementId, quantityAfter, false);
    }

    /**
     * 移位（库存转移）。
     * <p>
     * 事务编排顺序：校验源/目标不同 → 锁定批次 → 校验物品（允许归档物品） → 校验精度 → 校验位置 → 确定性锁定库存位（UUID排序防死锁） → 条件扣减源库存 → 创建/增加目标库存 → 插入 TRANSFER 流水 → 记录幂等 → 审计 → 发布事件。
     */
    @Transactional
    public InboundResult transfer(UUID householdId, UUID accountId, UUID lotId,
                                  UUID fromLocationId, UUID toLocationId,
                                  BigDecimal quantity, String memo, String idempotencyKey) {
        // 1. Validate source and target are different (defensive check)
        if (fromLocationId.equals(toLocationId)) {
            throw new IllegalStateException("fromLocationId and toLocationId must be different");
        }

        var prepared = ExistingLotQuantity.require(lotMapper, catalogApi, householdId, lotId, quantity);
        UUID itemId = prepared.itemId();
        BigDecimal validatedQty = prepared.validatedQty();

        // Check idempotency replay
        String requestHash = null;
        if (idempotencyKey != null) {
            requestHash = RequestHashing.sha256("TRANSFER:"
                    + itemId + ":" + lotId + ":" + fromLocationId + ":" + toLocationId + ":"
                    + validatedQty.scale() + ":" + validatedQty.stripTrailingZeros());
            var replayed = replayCached(householdId, idempotencyKey, requestHash, toLocationId);
            if (replayed.isPresent()) return replayed.get();
        }

        // 5. Validate locations
        locationApi.requireLocation(householdId, fromLocationId);
        locationApi.requireLocation(householdId, toLocationId);

        // 6. Lock stock positions in deterministic order (smaller UUID first) to prevent deadlocks
        UUID firstLocId;
        UUID secondLocId;
        if (fromLocationId.compareTo(toLocationId) < 0) {
            firstLocId = fromLocationId;
            secondLocId = toLocationId;
        } else {
            firstLocId = toLocationId;
            secondLocId = fromLocationId;
        }

        StockPositionEntity firstSp = stockPositionMapper.lockOne(householdId, lotId, firstLocId);
        StockPositionEntity secondSp = stockPositionMapper.lockOne(householdId, lotId, secondLocId);

        // Map back to from/to
        StockPositionEntity fromSp;
        StockPositionEntity toSp;
        if (firstLocId.equals(fromLocationId)) {
            fromSp = firstSp;
            toSp = secondSp;
        } else {
            fromSp = secondSp;
            toSp = firstSp;
        }

        // Source must exist with sufficient stock
        if (fromSp == null) {
            throw new InventoryInsufficientStockException();
        }

        // 7. Subtract from source (returns 0 when insufficient)
        int updated = stockPositionMapper.subtractIfSufficient(
                householdId, lotId, fromLocationId, validatedQty);
        if (updated == 0) {
            throw new InventoryInsufficientStockException();
        }

        // 8. Create target stock position if it doesn't exist, then add quantity
        if (toSp == null) {
            var newSp = new StockPositionEntity();
            newSp.setId(UUID.randomUUID());
            newSp.setHouseholdId(householdId);
            newSp.setLotId(lotId);
            newSp.setLocationId(toLocationId);
            newSp.setQuantity(BigDecimal.ZERO);
            newSp.setRevision(0L);
            newSp.setCreatedAt(OffsetDateTime.now());
            newSp.setUpdatedAt(OffsetDateTime.now());
            stockPositionMapper.insert(newSp);
        }
        stockPositionMapper.addQuantity(householdId, lotId, toLocationId, validatedQty);

        // 9. Insert TRANSFER movement (one movement with both from and to)
        UUID idempotencyKeyUuid = idempotencyKey != null
                ? UUID.fromString(idempotencyKey)
                : UUID.randomUUID();
        String idempotencyKeyStr = idempotencyKeyUuid.toString();
        UUID movementId = UUID.randomUUID();
        var movement = new MovementEntity();
        movement.setId(movementId);
        movement.setHouseholdId(householdId);
        movement.setLotId(lotId);
        movement.setItemId(itemId);
        movement.setType(MovementType.TRANSFER);
        movement.setQuantity(validatedQty);
        movement.setFromLocationId(fromLocationId);
        movement.setToLocationId(toLocationId);
        movement.setReason(null);
        movement.setMemo(memo);
        movement.setOperatorAccountId(accountId);
        movement.setBusinessTime(OffsetDateTime.now());
        movement.setCreatedAt(OffsetDateTime.now());
        movement.setIdempotencyKey(idempotencyKeyStr);
        movement.setReversalOf(null);
        movementMapper.insert(movement);

        // 10. Record idempotency
        if (idempotencyKey != null) {
            idempotencyService.recordSuccess(householdId, idempotencyKey,
                    requestHash, movementId, Map.of("lotId", lotId, "movementId", movementId));
        }

        // 11. Audit
        systemApi.recordAudit(new SystemApi.AuditEvent(
                SystemApi.AuditAction.INVENTORY_TRANSFER, ZijaAuditOutcome.SUCCESS,
                householdId, accountId, null, null, null,
                Map.of("lotId", lotId, "itemId", itemId,
                        "fromLocationId", fromLocationId, "toLocationId", toLocationId,
                        "quantity", validatedQty)));

        // 12. Publish event
        eventPublisher.publish(new StockChangedEvent(
                UUID.randomUUID(), householdId, lotId, itemId,
                MovementType.TRANSFER, validatedQty, fromLocationId, toLocationId,
                OffsetDateTime.now(), movementId, idempotencyKeyUuid,
                accountId, null, null));

        // 13. Return result (quantity at target location after transfer)
        var updatedToSp = stockPositionMapper.lockOne(householdId, lotId, toLocationId);
        BigDecimal quantityAfter = updatedToSp != null ? updatedToSp.getQuantity() : BigDecimal.ZERO;
        return new InboundResult(lotId, toLocationId, movementId, quantityAfter, false);
    }

    /**
     * 校验物品处于活跃状态（不存在或已归档 → {@link InventoryArchivedItemException}）。
     */
    private CatalogApi.ItemInfo requireActiveItemOrThrow(UUID householdId, UUID itemId) {
        try {
            return catalogApi.requireActiveItem(householdId, itemId);
        } catch (RuntimeException ex) {
            throw new InventoryArchivedItemException("item is archived or missing: " + itemId);
        }
    }

    /**
     * 幂等回放：命中缓存则返回缓存的入库结果，否则返回空。
     */
    private java.util.Optional<InboundResult> replayCached(UUID householdId, String idempotencyKey,
                                                           String requestHash, UUID locationId) {
        var cached = idempotencyService.lockOrFind(householdId, idempotencyKey, requestHash);
        if (cached.isEmpty()) return java.util.Optional.empty();
        var payload = cached.get().getResponsePayload();
        UUID cachedLotId = UUID.fromString(payload.get("lotId").toString());
        UUID cachedMovementId = UUID.fromString(payload.get("movementId").toString());
        var sp0 = stockPositionMapper.selectOne(new LambdaQueryWrapper<StockPositionEntity>()
                .eq(StockPositionEntity::getHouseholdId, householdId)
                .eq(StockPositionEntity::getLotId, cachedLotId)
                .eq(StockPositionEntity::getLocationId, locationId));
        BigDecimal quantityAfter = sp0 != null ? sp0.getQuantity() : BigDecimal.ZERO;
        return java.util.Optional.of(new InboundResult(cachedLotId, locationId, cachedMovementId, quantityAfter, false));
    }

    /**
     * 锁定库存位（必须存在）并条件扣减；不存在或不足则抛 {@link InventoryInsufficientStockException}。
     */
    private void subtractOrThrow(UUID householdId, UUID lotId, UUID locationId, BigDecimal qty) {
        StockPositionEntity sp = stockPositionMapper.lockOne(householdId, lotId, locationId);
        if (sp == null) {
            throw new InventoryInsufficientStockException();
        }
        int updated = stockPositionMapper.subtractIfSufficient(householdId, lotId, locationId, qty);
        if (updated == 0) {
            throw new InventoryInsufficientStockException();
        }
    }

    /**
     * 插入扣减类（CONSUME/LOSS）流水并返回流水 ID。
     */
    private UUID insertDeductionMovement(UUID householdId, UUID accountId, UUID lotId, UUID itemId,
                                         String type, BigDecimal quantity, UUID fromLocationId,
                                         String reason, String memo, String idempotencyKey) {
        UUID idempotencyKeyUuid = idempotencyKey != null
                ? UUID.fromString(idempotencyKey)
                : UUID.randomUUID();
        String idempotencyKeyStr = idempotencyKeyUuid.toString();
        UUID movementId = UUID.randomUUID();
        var movement = new MovementEntity();
        movement.setId(movementId);
        movement.setHouseholdId(householdId);
        movement.setLotId(lotId);
        movement.setItemId(itemId);
        movement.setType(type);
        movement.setQuantity(quantity);
        movement.setFromLocationId(fromLocationId);
        movement.setToLocationId(null);
        movement.setReason(reason);
        movement.setMemo(memo);
        movement.setOperatorAccountId(accountId);
        movement.setBusinessTime(OffsetDateTime.now());
        movement.setCreatedAt(OffsetDateTime.now());
        movement.setIdempotencyKey(idempotencyKeyStr);
        movement.setReversalOf(null);
        movementMapper.insert(movement);
        return movementId;
    }

    public record InboundNewLotCommand(
            UUID itemId,
            BigDecimal quantity,
            LocalDate purchaseDate,
            LocalDate productionDate,
            LocalDate expiryDate,
            String serialNumber,
            String memo,
            String idempotencyKey
    ) {}

    public record InboundResult(
            UUID lotId,
            UUID locationId,
            UUID movementId,
            BigDecimal quantityAfter,
            boolean serialDuplicated
    ) {}
}
