package com.zija.reminder.internal;

import com.zija.catalog.CatalogApi;
import com.zija.inventory.InventoryApi;
import com.zija.reminder.internal.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 提醒任务重算入口（事件与每日扫描共用）。
 * 对受影响 lot（临期）与 item（低库存）计算是否需新建/更新/自动关闭任务，同事务内写通知。
 */
@Service
public class ReminderReconciler {

    private final ReminderService reminderService;
    private final CatalogApi catalogApi;
    private final InventoryApi inventoryApi;
    private final TaskMapper taskMapper;
    private final NotificationMapper notificationMapper;
    private final Clock clock;

    public ReminderReconciler(ReminderService reminderService, CatalogApi catalogApi,
                               InventoryApi inventoryApi, TaskMapper taskMapper,
                               NotificationMapper notificationMapper, Clock clock) {
        this.reminderService = reminderService; this.catalogApi = catalogApi;
        this.inventoryApi = inventoryApi; this.taskMapper = taskMapper;
        this.notificationMapper = notificationMapper; this.clock = clock;
    }

    @Transactional
    public void reconcile(UUID householdId, List<UUID> affectedLotIds, List<UUID> affectedItemIds, boolean dailyScan) {
        var rule = reminderService.getOrCreateRule(householdId);
        LocalDate today = LocalDate.now(clock);
        OffsetDateTime now = OffsetDateTime.now(clock);

        // Build lotId -> LotInfo map from affected items (avoids broken findItemIdOfLot)
        Map<UUID, InventoryApi.LotInfo> lotMap = new HashMap<>();
        for (UUID itemId : affectedItemIds) {
            for (var lot : inventoryApi.lotsOfItem(householdId, itemId)) {
                lotMap.put(lot.lotId(), lot);
            }
        }

        for (UUID lotId : affectedLotIds) {
            reconcileExpiryLot(householdId, lotId, rule, today, now, lotMap);
        }
        // Reconcile low-stock for affected items (deduplicated)
        for (UUID itemId : affectedItemIds.stream().distinct().toList()) {
            reconcileLowStockItem(householdId, itemId, rule, now);
        }
    }

    private void reconcileExpiryLot(UUID householdId, UUID lotId, ReminderService.RuleView rule,
                                   LocalDate today, OffsetDateTime now,
                                   Map<UUID, InventoryApi.LotInfo> lotMap) {
        var lot = lotMap.get(lotId);
        if (lot == null || lot.totalQuantity().signum() <= 0) {
            // Lot consumed or not found: auto-close any open expiry task
            closeExistingExpiry(householdId, lotId, now, "LOT_CONSUMED");
            return;
        }
        if (lot.expiryDate() == null) return;
        var item = catalogApi.requireItem(householdId, lot.itemId());
        var eff = ReminderRuleResolver.resolveExpiry(item, rule);
        if (!eff.enabled()) {
            // Rule disabled: auto-close if there's an existing task
            closeExistingExpiry(householdId, lotId, now, "LOT_RECOVERED");
            return;
        }
        long daysLeft = ChronoUnit.DAYS.between(today, lot.expiryDate());
        short maxDay = eff.days().stream().max(Short::compare).orElse((short) 0);
        String severity = SeverityClassifier.expiry(maxDay, daysLeft);
        if (severity == null) return; // not in window

        var existing = taskMapper.lockOpenByKindAndTarget(householdId, "EXPIRY", lotId);
        if (existing != null) {
            existing.setDueAt(lot.expiryDate().atStartOfDay().atOffset(ZoneOffset.UTC));
            existing.setSeverity(severity);
            existing.setThresholdSnapshot(Map.of("days", eff.days().toString()));
            existing.setLastReconciledAt(now);
            existing.setUpdatedAt(now);
            taskMapper.updateForReconcile(existing);
        } else {
            var t = new TaskEntity();
            t.setId(UUID.randomUUID()); t.setHouseholdId(householdId);
            t.setKind("EXPIRY"); t.setLotId(lotId); t.setItemId(lot.itemId());
            t.setStatus("OPEN");
            t.setDueAt(lot.expiryDate().atStartOfDay().atOffset(ZoneOffset.UTC));
            t.setSeverity(severity);
            t.setThresholdSnapshot(Map.of("days", eff.days().toString()));
            t.setLastReconciledAt(now); t.setCreatedAt(now); t.setUpdatedAt(now); t.setVersion(0);
            try { taskMapper.insert(t); }
            catch (org.springframework.dao.DuplicateKeyException ignored) {
                return; // concurrent creation
            }
            writeNotification(householdId, "TASK_CREATED", t.getId(),
                    "「" + item.name() + "」批次将在 " + daysLeft + " 天内到期");
        }
    }

    private void closeExistingExpiry(UUID householdId, UUID lotId, OffsetDateTime now, String reason) {
        var existing = taskMapper.lockOpenByKindAndTarget(householdId, "EXPIRY", lotId);
        if (existing == null) return;
        existing.setStatus("DONE");
        var snap = existing.getThresholdSnapshot() == null ? new HashMap<String, Object>() : new HashMap<>(existing.getThresholdSnapshot());
        snap.put("autoClosed", true); snap.put("reason", reason);
        existing.setThresholdSnapshot(snap);
        existing.setSnoozedUntil(null);
        existing.setLastReconciledAt(now); existing.setUpdatedAt(now);
        taskMapper.updateForReconcile(existing);
        writeNotification(householdId, "TASK_CLOSED", existing.getId(), "临期任务已自动关闭");
    }

    private void reconcileLowStockItem(UUID householdId, UUID itemId, ReminderService.RuleView rule, OffsetDateTime now) {
        var item = catalogApi.requireItem(householdId, itemId);
        var eff = ReminderRuleResolver.resolveLowStock(item, rule);
        BigDecimal qty = inventoryApi.currentTotalStockOfItem(householdId, itemId);

        if (!eff.enabled()) {
            closeExistingLowStock(householdId, itemId, now, "RECOVERED");
            return;
        }
        BigDecimal threshold = eff.threshold();
        boolean belowThreshold = qty.compareTo(threshold) < 0;

        // For LOW_STOCK: lockOpenByKindAndTarget ignores lotId (SQL uses lot_id IS NULL).
        // There's at most one OPEN/SNOOZED LOW_STOCK task per household.
        var existing = taskMapper.lockOpenByKindAndTarget(householdId, "LOW_STOCK", itemId);
        if (belowThreshold) {
            String severity = SeverityClassifier.lowStock(qty, threshold);
            if (existing != null) {
                existing.setItemId(itemId);
                existing.setSeverity(severity);
                existing.setQtySnapshot(qty);
                existing.setThresholdSnapshot(Map.of("threshold", threshold.toString()));
                existing.setDueAt(now);
                existing.setLastReconciledAt(now); existing.setUpdatedAt(now);
                taskMapper.updateForReconcile(existing);
            } else {
                var t = new TaskEntity();
                t.setId(UUID.randomUUID()); t.setHouseholdId(householdId);
                t.setKind("LOW_STOCK"); t.setLotId(null); t.setItemId(itemId);
                t.setStatus("OPEN"); t.setDueAt(now); t.setSeverity(severity);
                t.setQtySnapshot(qty);
                t.setThresholdSnapshot(Map.of("threshold", threshold.toString()));
                t.setLastReconciledAt(now); t.setCreatedAt(now); t.setUpdatedAt(now); t.setVersion(0);
                try { taskMapper.insert(t); }
                catch (org.springframework.dao.DuplicateKeyException ignored) { return; }
                writeNotification(householdId, "TASK_CREATED", t.getId(),
                        "「" + item.name() + "」库存仅剩 " + qty + "，低于阈值 " + threshold);
            }
        } else {
            // Stock recovered, auto-close
            closeExistingLowStock(householdId, itemId, now, "RECOVERED");
        }
    }

    private void closeExistingLowStock(UUID householdId, UUID itemId, OffsetDateTime now, String reason) {
        var existing = taskMapper.lockOpenByKindAndTarget(householdId, "LOW_STOCK", itemId);
        if (existing == null) return;
        existing.setStatus("DONE");
        var snap = existing.getThresholdSnapshot() == null ? new HashMap<String, Object>() : new HashMap<>(existing.getThresholdSnapshot());
        snap.put("autoClosed", true); snap.put("reason", reason);
        existing.setThresholdSnapshot(snap);
        existing.setSnoozedUntil(null);
        existing.setLastReconciledAt(now); existing.setUpdatedAt(now);
        taskMapper.updateForReconcile(existing);
        writeNotification(householdId, "TASK_CLOSED", existing.getId(), "低库存任务已自动关闭");
    }

    private void writeNotification(UUID householdId, String scope, UUID taskId, String title) {
        var n = new NotificationEntity();
        n.setId(UUID.randomUUID()); n.setHouseholdId(householdId);
        n.setScope(scope); n.setTitle(title); n.setSourceTaskId(taskId);
        n.setRead(false); n.setCreatedAt(OffsetDateTime.now());
        notificationMapper.insert(n);
    }
}
