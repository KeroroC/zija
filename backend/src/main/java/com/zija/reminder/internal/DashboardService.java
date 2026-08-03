package com.zija.reminder.internal;

import com.zija.catalog.CatalogApi;
import com.zija.reminder.internal.persistence.TaskEntity;
import com.zija.reminder.internal.persistence.TaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
class DashboardService {

    private final TaskMapper taskMapper;
    private final CatalogApi catalogApi;
    private final Clock clock;

    DashboardService(TaskMapper taskMapper, CatalogApi catalogApi, Clock clock) {
        this.taskMapper = taskMapper;
        this.catalogApi = catalogApi;
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

        Map<UUID, String> names = loadItemNames(householdId, expiry, lowStock, priority);

        return new DashboardView(
                new DashboardGroup(
                        countAllExpiryWithinDays(householdId, from, to),
                        expiry.stream().map(t -> toItem(t, names)).toList()),
                new DashboardGroup(
                        countAllLowStock(householdId),
                        lowStock.stream().map(t -> toItem(t, names)).toList()),
                new DashboardGroup(
                        countAllPriority(householdId),
                        priority.stream().map(t -> toItem(t, names)).toList()),
                now
        );
    }

    private Map<UUID, String> loadItemNames(UUID hh, List<TaskEntity>... groups) {
        return catalogApi.itemNames(hh, Arrays.stream(groups)
                .flatMap(List::stream)
                .map(TaskEntity::getItemId)
                .collect(Collectors.toSet()));
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

    private DashboardItem toItem(TaskEntity t, Map<UUID, String> names) {
        String itemName = names.getOrDefault(t.getItemId(), t.getItemId().toString());
        String title = switch (t.getKind()) {
            case "EXPIRY" -> expiryTitle(itemName, t.getDueAt());
            case "LOW_STOCK" -> lowStockTitle(itemName, t);
            default -> itemName;
        };
        return new DashboardItem(t.getId(), t.getKind(), t.getSeverity(),
                title, t.getDueAt(), t.getItemId(), t.getLotId());
    }

    private String expiryTitle(String itemName, OffsetDateTime dueAt) {
        // dueAt 由 reconcile 存为到期日 UTC 零点，故 toLocalDate() 即到期日，与 reconcile 的 LocalDate 计算一致
        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(clock), dueAt.toLocalDate());
        if (daysLeft < 0) {
            return "「" + itemName + "」已过期 " + (-daysLeft) + " 天";
        }
        if (daysLeft == 0) {
            return "「" + itemName + "」今天到期";
        }
        return "「" + itemName + "」还有 " + daysLeft + " 天到期";
    }

    private String lowStockTitle(String itemName, TaskEntity t) {
        Object threshold = t.getThresholdSnapshot() == null ? null : t.getThresholdSnapshot().get("threshold");
        String thresholdText = threshold == null ? "?" : threshold.toString();
        String qtyText = t.getQtySnapshot() == null ? "?" : t.getQtySnapshot().stripTrailingZeros().toPlainString();
        return "「" + itemName + "」库存仅剩 " + qtyText + "，低于阈值 " + thresholdText;
    }

    record DashboardView(DashboardGroup expiryWithin7Days, DashboardGroup lowStockItems,
                         DashboardGroup priorityTasks, OffsetDateTime generatedAt) {}
    record DashboardGroup(long count, List<DashboardItem> items) {}
    record DashboardItem(UUID taskId, String kind, String severity, String title,
                         OffsetDateTime dueAt, UUID itemId, UUID lotId) {}
}
