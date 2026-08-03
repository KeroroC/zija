package com.zija.reminder.internal;

import com.zija.catalog.CatalogApi;
import com.zija.household.HouseholdApi;
import com.zija.inventory.InventoryApi;
import com.zija.reminder.internal.persistence.TaskEntity;
import com.zija.reminder.internal.persistence.TaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 每日临期扫描调度器。
 * <p>
 * 定时任务（默认每天 03:00）执行两步操作：
 * <ol>
 *   <li>将 snoozed_until 已过的 SNOOZED 任务批量转回 OPEN</li>
 *   <li>对当前家庭所有活跃物品的临期批次做全量重算（新建/刷新 severity / 自动关闭已消耗批次）</li>
 * </ol>
 */
@Service
public class ExpiryScanScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiryScanScheduler.class);

    private final TaskMapper taskMapper;
    private final ReminderReconciler reconciler;
    private final Clock clock;
    private final HouseholdApi householdApi;
    private final CatalogApi catalogApi;
    private final InventoryApi inventoryApi;

    public ExpiryScanScheduler(TaskMapper taskMapper, ReminderReconciler reconciler,
                                Clock clock, HouseholdApi householdApi,
                                CatalogApi catalogApi, InventoryApi inventoryApi) {
        this.taskMapper = taskMapper;
        this.reconciler = reconciler;
        this.clock = clock;
        this.householdApi = householdApi;
        this.catalogApi = catalogApi;
        this.inventoryApi = inventoryApi;
    }

    /** 生产调度：每天 03:00（Asia/Shanghai）。 */
    @Scheduled(cron = "${zija.schedule.expiry-scan:0 0 3 * * *}", zone = "${zija.schedule.zone:Asia/Shanghai}")
    @Transactional
    public void scanDaily() {
        scanAt(LocalDate.now(clock));
    }

    /** 测试入口：按指定「今天」扫描，便于 Clock 覆盖。 */
    @Transactional
    public void scanAt(LocalDate today) {
        OffsetDateTime now = today.atStartOfDay().atOffset(ZoneOffset.UTC);

        // 1. 批量刷新 SNOOZED 过期 → OPEN
        int refreshed = taskMapper.refreshSnoozedPast(now);
        if (refreshed > 0) {
            log.info("Expiry scan: refreshed {} snoozed tasks to OPEN", refreshed);
        }

        // 2. 获取当前家庭（单例模式）
        var household = householdApi.findHousehold();
        if (household.isEmpty()) {
            log.debug("Expiry scan: no household found, skipping");
            return;
        }
        UUID hhId = household.get().id();

        // 3-4. 收集活跃物品/批次并全量重算
        reconcileAllForHousehold(hhId);
    }

    /**
     * 对指定家庭全量重算：收集所有活跃物品的批次 ID（发现新进入窗口的批次），
     * 调用 reconciler 重算。不刷新 snoozed 到期。供每日扫描与规则变更监听复用。
     */
    @Transactional
    public void reconcileAllForHousehold(UUID hhId) {
        List<UUID> allLotIds = new ArrayList<>();
        List<UUID> allItemIds = new ArrayList<>();
        for (var item : catalogApi.listActiveItems(hhId)) {
            allItemIds.add(item.id());
            for (var lot : inventoryApi.lotsOfItem(hhId, item.id())) {
                if (lot.expiryDate() != null && lot.totalQuantity().signum() > 0) {
                    allLotIds.add(lot.lotId());
                }
            }
        }

        if (allLotIds.isEmpty() && allItemIds.isEmpty()) {
            log.debug("Expiry scan: no items/lots for household {}", hhId);
            return;
        }

        // 4. 调用 reconciler 全量重算
        reconciler.reconcile(hhId, allLotIds, allItemIds, true);
        log.info("Expiry scan: reconciled {} lots, {} items for household {}",
                allLotIds.size(), allItemIds.size(), hhId);
    }
}
