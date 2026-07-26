package com.zija.reminder.internal;

import com.zija.reminder.internal.persistence.TaskEntity;
import com.zija.reminder.internal.persistence.TaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
class DashboardService {

    private final TaskMapper taskMapper;
    private final Clock clock;

    DashboardService(TaskMapper taskMapper, Clock clock) {
        this.taskMapper = taskMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardView dashboard(UUID householdId, int days, int topN) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime from = now;
        OffsetDateTime to = now.plusDays(days);
        var expiry = taskMapper.expiryWithinDays(householdId, from, to, topN);
        var lowStock = taskMapper.lowStockOpenTasks(householdId, topN);
        var priority = taskMapper.priorityTasks(householdId, topN);

        return new DashboardView(
                new DashboardGroup(
                        countAllExpiryWithinDays(householdId, from, to),
                        expiry.stream().map(this::toExpiryItem).toList()),
                new DashboardGroup(
                        countAllLowStock(householdId),
                        lowStock.stream().map(this::toLowStockItem).toList()),
                new DashboardGroup(
                        countAllPriority(householdId),
                        priority.stream().map(this::toPriorityItem).toList()),
                now
        );
    }

    private long countAllExpiryWithinDays(UUID hh, OffsetDateTime from, OffsetDateTime to) {
        return taskMapper.expiryWithinDays(hh, from, to, Integer.MAX_VALUE).size();
    }

    private long countAllLowStock(UUID hh) {
        return taskMapper.lowStockOpenTasks(hh, Integer.MAX_VALUE).size();
    }

    private long countAllPriority(UUID hh) {
        return taskMapper.priorityTasks(hh, Integer.MAX_VALUE).size();
    }

    private DashboardItem toExpiryItem(TaskEntity t) {
        return new DashboardItem(t.getId(), t.getKind(), t.getSeverity(),
                "临期任务", t.getDueAt(), t.getItemId(), t.getLotId());
    }

    private DashboardItem toLowStockItem(TaskEntity t) {
        return new DashboardItem(t.getId(), t.getKind(), t.getSeverity(),
                "低库存任务", t.getDueAt(), t.getItemId(), null);
    }

    private DashboardItem toPriorityItem(TaskEntity t) {
        return new DashboardItem(t.getId(), t.getKind(), t.getSeverity(),
                "优先任务", t.getDueAt(), t.getItemId(), t.getLotId());
    }

    record DashboardView(DashboardGroup expiryWithin7Days, DashboardGroup lowStockItems,
                         DashboardGroup priorityTasks, OffsetDateTime generatedAt) {}
    record DashboardGroup(long count, List<DashboardItem> items) {}
    record DashboardItem(UUID taskId, String kind, String severity, String title,
                         OffsetDateTime dueAt, UUID itemId, UUID lotId) {}
}
