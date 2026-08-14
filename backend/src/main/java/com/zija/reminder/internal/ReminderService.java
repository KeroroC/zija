package com.zija.reminder.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zija.shared.ZijaAuditOutcome;
import com.zija.reminder.internal.exception.ReminderRuleExpiryDaysInvalidException;
import com.zija.reminder.internal.exception.ReminderRuleLowStockInvalidException;
import com.zija.reminder.internal.exception.ReminderRuleVersionConflictException;
import com.zija.reminder.internal.persistence.HouseholdRuleEntity;
import com.zija.reminder.internal.persistence.HouseholdRuleMapper;
import com.zija.reminder.internal.persistence.NotificationEntity;
import com.zija.reminder.internal.persistence.NotificationMapper;
import com.zija.reminder.internal.persistence.TaskEntity;
import com.zija.reminder.internal.persistence.TaskMapper;
import com.zija.system.SystemApi;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
class ReminderService {

    private final HouseholdRuleMapper ruleMapper;
    private final NotificationMapper notificationMapper;
    private final TaskMapper taskMapper;
    private final SystemApi systemApi;
    private final ApplicationEventPublisher eventPublisher;

    ReminderService(HouseholdRuleMapper ruleMapper, NotificationMapper notificationMapper,
                    TaskMapper taskMapper, SystemApi systemApi,
                    ApplicationEventPublisher eventPublisher) {
        this.ruleMapper = ruleMapper;
        this.notificationMapper = notificationMapper;
        this.taskMapper = taskMapper;
        this.systemApi = systemApi;
        this.eventPublisher = eventPublisher;
    }

    record RuleView(UUID id, UUID householdId, boolean expiryDisabled, List<Short> expiryReminderDays,
                    boolean lowStockDisabled, BigDecimal lowStockThreshold, int version) {}
    record RuleUpdate(boolean expiryDisabled, List<Short> expiryReminderDays,
                      boolean lowStockDisabled, BigDecimal lowStockThreshold, int version) {}

    /** Lazy-initialize household default rule (spec: 30/7/1 days, low-stock threshold 1). */
    @Transactional
    public RuleView getOrCreateRule(UUID householdId) {
        var wrapper = new LambdaQueryWrapper<HouseholdRuleEntity>()
                .eq(HouseholdRuleEntity::getHouseholdId, householdId);
        HouseholdRuleEntity e = LazyInit.getOrCreate(
                () -> ruleMapper.selectOne(wrapper),
                () -> createDefaultRule(householdId),
                ruleMapper::insert);
        return toView(e);
    }

    private HouseholdRuleEntity createDefaultRule(UUID householdId) {
        var e = new HouseholdRuleEntity();
        e.setId(UUID.randomUUID());
        e.setHouseholdId(householdId);
        e.setExpiryDisabled(false);
        e.setExpiryReminderDays(List.of((short) 30, (short) 7, (short) 1));
        e.setLowStockDisabled(false);
        e.setLowStockThreshold(BigDecimal.ONE);
        e.setCreatedAt(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());
        e.setVersion(0);
        return e;
    }

    @Transactional
    public RuleView updateRule(UUID householdId, RuleUpdate update) {
        validateUpdate(update);
        var current = ruleMapper.selectOne(new LambdaQueryWrapper<HouseholdRuleEntity>()
                .eq(HouseholdRuleEntity::getHouseholdId, householdId));
        if (current == null) {
            // lazy init first, then re-read
            getOrCreateRule(householdId);
            current = ruleMapper.selectOne(new LambdaQueryWrapper<HouseholdRuleEntity>()
                    .eq(HouseholdRuleEntity::getHouseholdId, householdId));
        }
        if (current.getVersion() != update.version()) {
            throw new ReminderRuleVersionConflictException();
        }
        boolean changed = !Boolean.TRUE.equals(current.getExpiryDisabled())
                || !Objects.equals(current.getExpiryReminderDays(), update.expiryReminderDays())
                || !Boolean.TRUE.equals(current.getLowStockDisabled())
                || !Objects.equals(current.getLowStockThreshold(), update.lowStockThreshold());
        current.setExpiryDisabled(update.expiryDisabled());
        current.setExpiryReminderDays(update.expiryReminderDays());
        current.setLowStockDisabled(update.lowStockDisabled());
        current.setLowStockThreshold(update.lowStockThreshold());
        current.setUpdatedAt(OffsetDateTime.now());
        int rows = ruleMapper.updateById(current); // optimistic lock
        if (rows == 0) throw new ReminderRuleVersionConflictException();
        writeRuleChangedNotification(householdId);
        systemApi.recordAudit(new SystemApi.AuditEvent(
                "REMINDER_RULE_UPDATE", ZijaAuditOutcome.SUCCESS, householdId, null, null, null, null,
                Map.of("version", String.valueOf(update.version()))));
        if (changed) {
            eventPublisher.publishEvent(new ReminderRuleChangedEvent(householdId));
        }
        return toView(current);
    }

    private void validateUpdate(RuleUpdate u) {
        if (!u.expiryDisabled()) {
            if (u.expiryReminderDays() == null || u.expiryReminderDays().isEmpty())
                throw new ReminderRuleExpiryDaysInvalidException();
            // 1..3650, unique
            var days = u.expiryReminderDays().stream().distinct().toList();
            if (days.size() != u.expiryReminderDays().size()) throw new ReminderRuleExpiryDaysInvalidException();
            for (short d : days) if (d < 1 || d > 3650) throw new ReminderRuleExpiryDaysInvalidException();
            // descending
            for (int i = 1; i < days.size(); i++) {
                if (days.get(i - 1) <= days.get(i)) throw new ReminderRuleExpiryDaysInvalidException();
            }
        }
        if (!u.lowStockDisabled()) {
            if (u.lowStockThreshold() == null || u.lowStockThreshold().signum() <= 0)
                throw new ReminderRuleLowStockInvalidException();
        }
    }

    private void writeRuleChangedNotification(UUID householdId) {
        var n = new NotificationEntity();
        n.setId(UUID.randomUUID());
        n.setHouseholdId(householdId);
        n.setScope("RULE_CHANGED");
        n.setTitle("家庭默认提醒规则已更新");
        n.setRead(false);
        n.setCreatedAt(OffsetDateTime.now());
        notificationMapper.insert(n);
    }

    private RuleView toView(HouseholdRuleEntity e) {
        return new RuleView(e.getId(), e.getHouseholdId(),
                Boolean.TRUE.equals(e.getExpiryDisabled()), e.getExpiryReminderDays(),
                Boolean.TRUE.equals(e.getLowStockDisabled()), e.getLowStockThreshold(),
                e.getVersion() == null ? 0 : e.getVersion());
    }

    // ==================== Task page query ====================

    @Transactional(readOnly = true)
    public TaskPage tasksPage(UUID householdId, String kind, String status, UUID itemId,
                              boolean overdue, int page, int pageSize, String orderBy) {
        Boolean overdueFlag = overdue ? Boolean.TRUE : null;
        var p = taskMapper.findPage(
                new Page<>(page, pageSize),
                householdId, kind, status, itemId, overdueFlag,
                OffsetDateTime.now(), orderBy);
        var items = p.getRecords().stream().map(this::toTaskView).toList();
        return new TaskPage(items, p.getTotal(), (int) p.getCurrent(), (int) p.getSize());
    }

    private TaskView toTaskView(TaskEntity e) {
        return new TaskView(e.getId(), e.getKind(), e.getLotId(), e.getItemId(),
                e.getStatus(), e.getDueAt(), e.getSeverity(), e.getSnoozedUntil());
    }

    record TaskView(UUID id, String kind, UUID lotId, UUID itemId, String status,
                    OffsetDateTime dueAt, String severity, OffsetDateTime snoozedUntil) {}

    record TaskPage(List<TaskView> items, long total, int page, int pageSize) {}
}
