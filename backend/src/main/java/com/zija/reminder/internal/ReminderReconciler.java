package com.zija.reminder.internal;

import com.zija.shared.ZijaAuditOutcome;
import com.zija.catalog.CatalogApi;
import com.zija.inventory.InventoryApi;
import com.zija.reminder.internal.mail.MailService;
import com.zija.reminder.internal.mail.MailSettingService;
import com.zija.reminder.internal.mail.MailTemplateRenderer;
import com.zija.reminder.internal.persistence.*;
import com.zija.system.SystemApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 提醒任务重算入口（事件与每日扫描共用）。
 * 对受影响 lot（临期）与 item（低库存）计算是否需新建/更新/自动关闭任务，同事务内写通知。
 */
@Service
class ReminderReconciler {

    private static final Logger log = LoggerFactory.getLogger(ReminderReconciler.class);
    private static final String REASON_LOT_CONSUMED = "LOT_CONSUMED";
    private static final String REASON_LOT_RECOVERED = "LOT_RECOVERED";
    private static final String REASON_OUT_OF_WINDOW = "OUT_OF_WINDOW";
    private static final String REASON_RECOVERED = "RECOVERED";

    private final ReminderService reminderService;
    private final CatalogApi catalogApi;
    private final InventoryApi inventoryApi;
    private final TaskMapper taskMapper;
    private final NotificationMapper notificationMapper;
    private final MailService mailService;
    private final MailSettingService mailSettingService;
    private final MailTemplateRenderer templateRenderer;
    private final SystemApi systemApi;
    private final MemberEmails memberEmails;
    private final Clock clock;

    public ReminderReconciler(ReminderService reminderService, CatalogApi catalogApi,
                               InventoryApi inventoryApi, TaskMapper taskMapper,
                               NotificationMapper notificationMapper,
                               MailService mailService, MailSettingService mailSettingService,
                               MailTemplateRenderer templateRenderer, SystemApi systemApi,
                               MemberEmails memberEmails,
                               @org.springframework.beans.factory.annotation.Qualifier(ClockConfig.REMINDER_CLOCK) Clock clock) {
        this.reminderService = reminderService; this.catalogApi = catalogApi;
        this.inventoryApi = inventoryApi; this.taskMapper = taskMapper;
        this.notificationMapper = notificationMapper;
        this.mailService = mailService; this.mailSettingService = mailSettingService;
        this.templateRenderer = templateRenderer; this.systemApi = systemApi;
        this.memberEmails = memberEmails; this.clock = clock;
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
            closeExistingTask(householdId, TaskKind.EXPIRY, lotId, null, now, REASON_LOT_CONSUMED, "临期任务已自动关闭");
            return;
        }
        if (lot.expiryDate() == null) return;
        var item = catalogApi.requireItem(householdId, lot.itemId());
        var eff = ReminderRuleResolver.resolveExpiry(item, rule);
        if (!eff.enabled()) {
            // Rule disabled: auto-close if there's an existing task
            closeExistingTask(householdId, TaskKind.EXPIRY, lotId, null, now, REASON_LOT_RECOVERED, "临期任务已自动关闭");
            return;
        }
        long daysLeft = ChronoUnit.DAYS.between(today, lot.expiryDate());
        short maxDay = eff.days().stream().max(Short::compare).orElse((short) 0);
        String severity = SeverityClassifier.expiry(maxDay, daysLeft);
        if (severity == null) {
            // 不在提醒窗口（如规则窗口收窄后批次落出窗口）：关闭既有 OPEN 任务，避免残留过期任务
            closeExistingTask(householdId, TaskKind.EXPIRY, lotId, null, now, REASON_OUT_OF_WINDOW, "临期任务已自动关闭");
            return;
        }

        var existing = taskMapper.lockOpenByKindAndTarget(householdId, TaskKind.EXPIRY, lotId, null);
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
            t.setKind(TaskKind.EXPIRY); t.setLotId(lotId); t.setItemId(lot.itemId());
            t.setStatus(TaskStatus.OPEN);
            t.setDueAt(lot.expiryDate().atStartOfDay().atOffset(ZoneOffset.UTC));
            t.setSeverity(severity);
            t.setThresholdSnapshot(Map.of("days", eff.days().toString()));
            t.setLastReconciledAt(now); t.setCreatedAt(now); t.setUpdatedAt(now); t.setVersion(0);
            try { taskMapper.insert(t); }
            catch (org.springframework.dao.DuplicateKeyException ignored) {
                return; // concurrent creation
            }
            writeNotification(householdId, NotificationScope.TASK_CREATED, t.getId(),
                    "「" + item.name() + "」批次将在 " + daysLeft + " 天内到期");
            triggerUrgentMail(householdId, severity, item.name(), t.getId());
        }
    }

    private void closeExistingTask(UUID householdId, String kind, UUID lotId, UUID itemId,
                                   OffsetDateTime now, String reason, String message) {
        var existing = taskMapper.lockOpenByKindAndTarget(householdId, kind, lotId, itemId);
        if (existing == null) return;
        existing.setStatus(TaskStatus.DONE);
        var snap = existing.getThresholdSnapshot() == null ? new HashMap<String, Object>() : new HashMap<>(existing.getThresholdSnapshot());
        snap.put("autoClosed", true); snap.put("reason", reason);
        existing.setThresholdSnapshot(snap);
        existing.setSnoozedUntil(null);
        existing.setLastReconciledAt(now); existing.setUpdatedAt(now);
        taskMapper.updateForReconcile(existing);
        writeNotification(householdId, NotificationScope.TASK_CLOSED, existing.getId(), message);
    }

    private void reconcileLowStockItem(UUID householdId, UUID itemId, ReminderService.RuleView rule, OffsetDateTime now) {
        var item = catalogApi.requireItem(householdId, itemId);
        var eff = ReminderRuleResolver.resolveLowStock(item, rule);
        BigDecimal qty = inventoryApi.currentTotalStockOfItem(householdId, itemId);

        if (!eff.enabled()) {
            closeExistingTask(householdId, TaskKind.LOW_STOCK, null, itemId, now, REASON_RECOVERED, "低库存任务已自动关闭");
            return;
        }
        BigDecimal threshold = eff.threshold();
        boolean belowThreshold = qty.compareTo(threshold) < 0;

        // LOW_STOCK：每户每个 item 一条未完成任务（唯一索引 uq_reminder_task_lowstock_open）。
        var existing = taskMapper.lockOpenByKindAndTarget(householdId, TaskKind.LOW_STOCK, null, itemId);
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
                t.setKind(TaskKind.LOW_STOCK); t.setLotId(null); t.setItemId(itemId);
                t.setStatus(TaskStatus.OPEN); t.setDueAt(now); t.setSeverity(severity);
                t.setQtySnapshot(qty);
                t.setThresholdSnapshot(Map.of("threshold", threshold.toString()));
                t.setLastReconciledAt(now); t.setCreatedAt(now); t.setUpdatedAt(now); t.setVersion(0);
                try { taskMapper.insert(t); }
                catch (org.springframework.dao.DuplicateKeyException ignored) { return; }
                writeNotification(householdId, NotificationScope.TASK_CREATED, t.getId(),
                        "「" + item.name() + "」库存仅剩 " + qty + "，低于阈值 " + threshold);
                triggerUrgentMail(householdId, severity, item.name(), t.getId());
            }
        } else {
            // Stock recovered, auto-close
            closeExistingTask(householdId, TaskKind.LOW_STOCK, null, itemId, now, REASON_RECOVERED, "低库存任务已自动关闭");
        }
    }

    private void writeNotification(UUID householdId, String scope, UUID taskId, String title) {
        var n = new NotificationEntity();
        n.setId(UUID.randomUUID()); n.setHouseholdId(householdId);
        n.setScope(scope); n.setTitle(title); n.setSourceTaskId(taskId);
        n.setRead(false); n.setCreatedAt(OffsetDateTime.now());
        notificationMapper.insert(n);
    }

    /**
     * URGENT 任务创建后触发紧急邮件（失败不阻塞主流程）。
     */
    private void triggerUrgentMail(UUID householdId, String severity, String itemName, UUID taskId) {
        if (!TaskSeverity.URGENT.equals(severity) || !mailService.isConfigured()) return;
        try {
            var mailSetting = mailSettingService.getOrCreate(householdId);
            if (!mailSetting.urgentEnabled()) return;

            List<String> emails = memberEmails.findByRoles(householdId, mailSetting.recipientRoles());
            if (emails.isEmpty()) return;

            String html = templateRenderer.renderUrgent(Map.of(
                    "title", "「" + itemName + "」需要紧急关注",
                    "severity", severity,
                    "link", ""));

            for (String email : emails) {
                if (!mailService.send(email, "知家 · 紧急提醒", html)) {
                    throw new RuntimeException("SMTP send failed to " + email);
                }
            }
            log.info("Urgent mail sent for task {} to {} recipients", taskId, emails.size());
        } catch (RuntimeException ex) {
            log.warn("Urgent mail trigger failed for task {}: {}", taskId, ex.getMessage());
            try {
                systemApi.recordAudit(new SystemApi.AuditEvent(
                        SystemApi.AuditAction.MAIL_SEND_FAILED, ZijaAuditOutcome.FAILURE,
                        householdId, null, null, null, null,
                        Map.of("taskId", taskId.toString(), "reason",
                                ex.getMessage() != null ? ex.getMessage() : "unknown")));
            } catch (RuntimeException auditEx) {
                log.warn("Audit write also failed: {}", auditEx.getMessage());
            }
        }
    }
}
