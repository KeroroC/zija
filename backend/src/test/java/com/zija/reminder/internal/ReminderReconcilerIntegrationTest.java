package com.zija.reminder.internal;

import com.zija.TestDb;
import com.zija.catalog.CatalogApi;
import com.zija.catalog.internal.persistence.*;
import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.inventory.InventoryApi;
import com.zija.inventory.internal.ReversalService;
import com.zija.inventory.internal.StockCommandService;
import com.zija.location.internal.persistence.LocationEntity;
import com.zija.location.internal.persistence.LocationMapper;
import com.zija.reminder.internal.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.zija.SharedPostgres;

@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class ReminderReconcilerIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired ReminderReconciler reconciler;
    @Autowired ReminderService reminderService;
    @Autowired TaskMapper taskMapper;
    @Autowired NotificationMapper notificationMapper;
    @Autowired HouseholdMapper householdMapper;
    @Autowired ItemMapper itemMapper;
    @Autowired UnitMapper unitMapper;
    @Autowired LocationMapper locationMapper;
    @Autowired StockCommandService stockCommandService;
    @Autowired ReversalService reversalService;
    @Autowired InventoryApi inventoryApi;
    @Autowired CatalogApi catalogApi;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager txManager;

    private UUID householdId, itemId, unitId, locA, locB, accountId;

    @BeforeEach
    void setUp() {
        TestDb.cleanAll(jdbc);
        var hh = new HouseholdEntity(); hh.setSingletonKey((short) 1); hh.setId(UUID.randomUUID()); hh.setName("T"); hh.setTimezone("Asia/Shanghai");
        householdMapper.insertSingleton(hh); householdId = hh.getId();
        var u = new UnitEntity(); u.setId(UUID.randomUUID()); u.setHouseholdId(householdId);
        u.setName("个"); u.setNameNormalized("个"); u.setDecimalScale((short) 0); u.setStatus("ACTIVE"); unitMapper.insert(u); unitId = u.getId();
        locA = seedLoc("A"); locB = seedLoc("B");
        accountId = UUID.randomUUID();
        // Seed account for stock commands (FK constraint)
        jdbc.update("INSERT INTO account (id, username, username_normalized, password_hash, display_name) VALUES (?, ?, ?, 'hash', '测试')",
                accountId, "user" + accountId.toString().substring(0, 6), "user" + accountId.toString().substring(0, 6));
    }

    private UUID seedLoc(String name) {
        var l = new LocationEntity(); l.setId(UUID.randomUUID()); l.setHouseholdId(householdId);
        l.setName(name); l.setNameNormalized(name); l.setSortOrder(0); locationMapper.insert(l); return l.getId();
    }
    private UUID seedItem(String em, List<Short> days, String lm, BigDecimal t) {
        var it = new ItemEntity(); it.setId(UUID.randomUUID()); it.setHouseholdId(householdId);
        it.setName("牛奶"); it.setManagementType("CONSUMABLE"); it.setUnitId(unitId); it.setStatus("ACTIVE");
        it.setExpiryReminderMode(em); it.setExpiryReminderDays(days);
        it.setLowStockMode(lm); it.setLowStockThreshold(t); itemMapper.insert(it); return it.getId();
    }
    private UUID inboundLot(UUID itemId, BigDecimal qty, LocalDate expiry) {
        var cmd = new StockCommandService.InboundNewLotCommand(
                itemId, qty, LocalDate.now(), null, expiry, null, null, null);
        var r = new TransactionTemplate(txManager).execute(s ->
                stockCommandService.inboundNewLot(householdId, accountId, locA, cmd));
        return r.lotId();
    }

    @Test
    void inboundExpiringLot_createsExpiryOpenTaskAndNotification() {
        itemId = seedItem("INHERIT", null, "INHERIT", null);
        reminderService.getOrCreateRule(householdId);
        var lotId = inboundLot(itemId, BigDecimal.TEN, LocalDate.now().plusDays(5));

        new TransactionTemplate(txManager).executeWithoutResult(s ->
                reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));

        var tasks = taskMapper.selectList(null);
        assertThat(tasks).hasSize(1);
        var t = tasks.get(0);
        assertThat(t.getKind()).isEqualTo("EXPIRY");
        assertThat(t.getLotId()).isEqualTo(lotId);
        assertThat(t.getStatus()).isEqualTo("OPEN");
        assertThat(t.getSeverity()).isEqualTo("WARN"); // 5<=7
        var notifs = notificationMapper.selectList(null);
        assertThat(notifs).anyMatch(n -> "TASK_CREATED".equals(n.getScope()));
    }

    @Test
    void inboundFarFutureLot_createsNoTask() {
        itemId = seedItem("INHERIT", null, "INHERIT", null);
        reminderService.getOrCreateRule(householdId);
        var lotId = inboundLot(itemId, BigDecimal.TEN, LocalDate.now().plusDays(300));

        new TransactionTemplate(txManager).executeWithoutResult(s ->
                reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));

        assertThat(taskMapper.selectList(null)).isEmpty();
    }

    @Test
    void itemExpiryDisabled_createsNoExpiryTask() {
        itemId = seedItem("DISABLED", null, "INHERIT", null);
        reminderService.getOrCreateRule(householdId);
        var lotId = inboundLot(itemId, BigDecimal.TEN, LocalDate.now().plusDays(5));

        new TransactionTemplate(txManager).executeWithoutResult(s ->
                reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));

        assertThat(taskMapper.selectList(null)).noneMatch(t -> "EXPIRY".equals(t.getKind()));
    }

    @Test
    void householdExpiryDisabled_createsNoExpiryTask() {
        itemId = seedItem("INHERIT", null, "INHERIT", null);
        var rule = reminderService.getOrCreateRule(householdId);
        reminderService.updateRule(householdId, new ReminderService.RuleUpdate(
                true, List.of((short)60), false, BigDecimal.ONE, rule.version()));
        var lotId = inboundLot(itemId, BigDecimal.TEN, LocalDate.now().plusDays(5));

        new TransactionTemplate(txManager).executeWithoutResult(s ->
                reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));

        assertThat(taskMapper.selectList(null)).noneMatch(t -> "EXPIRY".equals(t.getKind()));
    }

    @Test
    void consumeClearingLot_autoClosesExpiryTask() {
        // Use lowStockMode=DISABLED to avoid LOW_STOCK task interference
        itemId = seedItem("INHERIT", null, "DISABLED", null);
        reminderService.getOrCreateRule(householdId);
        var lotId = inboundLot(itemId, BigDecimal.TEN, LocalDate.now().plusDays(5));

        var tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));
        assertThat(taskMapper.selectList(null)).hasSize(1); // EXPIRY OPEN

        // consume all
        tx.execute(s -> stockCommandService.consume(householdId, accountId, lotId, locA,
                BigDecimal.TEN, "用完", null, UUID.randomUUID().toString()));
        tx.executeWithoutResult(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));

        var tasks = taskMapper.selectList(null);
        assertThat(tasks).allMatch(t -> "DONE".equals(t.getStatus()));
        assertThat(notificationMapper.selectList(null)).anyMatch(n -> "TASK_CLOSED".equals(n.getScope()));
    }

    @Test
    void inboundRaisingStockAboveLowStockThreshold_autoClosesLowStockTask() {
        itemId = seedItem("INHERIT", null, "INHERIT", null); // default low stock threshold = 1
        reminderService.getOrCreateRule(householdId);
        // Inbound 1 unit with far-future expiry (no EXPIRY task)
        var lotId = inboundLot(itemId, BigDecimal.ONE, LocalDate.now().plusDays(300));

        var tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));
        // qty=1 >= threshold 1, no low stock task. Change threshold to 2
        var rule = reminderService.getOrCreateRule(householdId);
        reminderService.updateRule(householdId, new ReminderService.RuleUpdate(
                false, List.of((short)30,(short)7,(short)1), false, new BigDecimal("2"), rule.version()));
        tx.executeWithoutResult(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));
        assertThat(taskMapper.selectList(null)).anyMatch(t -> "LOW_STOCK".equals(t.getKind()) && "OPEN".equals(t.getStatus()));

        // inbound 5 to make stock = 6 > 2
        tx.execute(s -> stockCommandService.inboundExistingLot(householdId, accountId, locA, lotId,
                new BigDecimal("5"), null, UUID.randomUUID().toString()));
        tx.executeWithoutResult(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));

        assertThat(taskMapper.selectList(null)).filteredOn(t -> "LOW_STOCK".equals(t.getKind()))
                .allMatch(t -> "DONE".equals(t.getStatus()));
    }

    @Test
    void consumeDroppingBelowThreshold_createsLowStockTask() {
        itemId = seedItem("INHERIT", null, "CUSTOM", new BigDecimal("5"));
        reminderService.getOrCreateRule(householdId);
        var lotId = inboundLot(itemId, new BigDecimal("10"), LocalDate.now().plusDays(300));
        var tx = new TransactionTemplate(txManager);
        tx.execute(s -> stockCommandService.consume(householdId, accountId, lotId, locA,
                new BigDecimal("8"), "用", null, UUID.randomUUID().toString())); // remaining 2 < 5
        tx.executeWithoutResult(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));
        assertThat(taskMapper.selectList(null)).anyMatch(t -> "LOW_STOCK".equals(t.getKind()) && "OPEN".equals(t.getStatus()));
    }

    @Test
    void transferWithinSameItem_doesNotCreateOrCloseLowStockTask() {
        itemId = seedItem("INHERIT", null, "CUSTOM", new BigDecimal("5"));
        reminderService.getOrCreateRule(householdId);
        var lotId = inboundLot(itemId, new BigDecimal("10"), LocalDate.now().plusDays(300));
        var tx = new TransactionTemplate(txManager);
        tx.execute(s -> stockCommandService.transfer(householdId, accountId, lotId, locA, locB,
                new BigDecimal("3"), null, UUID.randomUUID().toString())); // total still 10
        tx.executeWithoutResult(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));
        assertThat(taskMapper.selectList(null)).filteredOn(t -> "LOW_STOCK".equals(t.getKind())).isEmpty();
    }

    @Test
    void reversalAutoClosesExpiryTask() {
        // Use lowStockMode=DISABLED to avoid LOW_STOCK task interference
        itemId = seedItem("INHERIT", null, "DISABLED", null);
        reminderService.getOrCreateRule(householdId);
        var lotId = inboundLot(itemId, BigDecimal.TEN, LocalDate.now().plusDays(5));
        var tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));
        // reverse the inbound movement
        var movements = inventoryApi.movementsOfLot(householdId, lotId);
        tx.execute(s -> reversalService.reverse(householdId, accountId,
                movements.get(0).id(), "录错", null, UUID.randomUUID().toString()));
        tx.executeWithoutResult(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));
        assertThat(taskMapper.selectList(null)).filteredOn(t -> "EXPIRY".equals(t.getKind()))
                .allMatch(t -> "DONE".equals(t.getStatus()));
    }

    @Test
    void snoozedTaskStillInWindow_keepsSnoozedUntil() {
        // Use lowStockMode=DISABLED to avoid LOW_STOCK task interference
        itemId = seedItem("INHERIT", null, "DISABLED", null);
        reminderService.getOrCreateRule(householdId);
        var lotId = inboundLot(itemId, BigDecimal.TEN, LocalDate.now().plusDays(5));
        var tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));
        // snooze
        var taskId = taskMapper.selectList(null).get(0).getId();
        var until = OffsetDateTime.now().plusDays(2);
        tx.execute(s -> taskMapper.snooze(householdId, taskId, List.of("OPEN","SNOOZED"), until));
        // reconcile again (still in risk window)
        tx.executeWithoutResult(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));

        var t = taskMapper.selectById(taskId);
        assertThat(t.getStatus()).isEqualTo("SNOOZED");
        assertThat(t.getSnoozedUntil()).isNotNull();
    }

    @Test
    void ignoredOrDoneTask_reconcileDoesNotReopen() {
        // Use DISABLED expiry + CUSTOM low stock so only LOW_STOCK task is created
        itemId = seedItem("DISABLED", null, "CUSTOM", new BigDecimal("5"));
        reminderService.getOrCreateRule(householdId);
        var lotId = inboundLot(itemId, new BigDecimal("2"), LocalDate.now().plusDays(300));
        var tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));
        var tasks = taskMapper.selectList(null);
        assertThat(tasks).hasSize(1);
        var taskId = tasks.get(0).getId();
        tx.execute(s -> taskMapper.transitionTo(householdId, taskId, List.of("OPEN","SNOOZED"), "IGNORED"));
        // reconcile (still in window)
        tx.executeWithoutResult(s -> reconciler.reconcile(householdId, List.of(lotId), List.of(itemId), false));
        assertThat(taskMapper.selectById(taskId).getStatus()).isEqualTo("IGNORED");
    }

    @Test
    void reconcile_threeLowStockItems_createsThreeIndependentTasks() {
        // 三个独立物品，全部低于阈值。CUSTOM low-stock threshold = 5。
        // 每个物品 inbound 1 unit (< 5)，应各自产生一条 LOW_STOCK OPEN 任务。
        reminderService.getOrCreateRule(householdId);
        var tx = new TransactionTemplate(txManager);

        String[] names = {"牛奶", "鸡蛋", "面粉"};
        BigDecimal threshold = new BigDecimal("5");
        java.util.List<UUID> itemIds = new java.util.ArrayList<>();
        java.util.List<UUID> lotIds = new java.util.ArrayList<>();
        for (String n : names) {
            var it = new ItemEntity();
            it.setId(UUID.randomUUID());
            it.setHouseholdId(householdId);
            it.setName(n);
            it.setManagementType("CONSUMABLE");
            it.setUnitId(unitId);
            it.setStatus("ACTIVE");
            it.setExpiryReminderMode("DISABLED");
            it.setLowStockMode("CUSTOM");
            it.setLowStockThreshold(threshold);
            itemMapper.insert(it);
            UUID iid = it.getId();
            itemIds.add(iid);
            UUID lid = inboundLot(iid, BigDecimal.ONE, LocalDate.now().plusDays(300));
            lotIds.add(lid);
        }

        // 单次 reconcile，把三个 item 一起传入（与 dailyScan.reconcileAllForHousehold 路径一致）
        tx.executeWithoutResult(s ->
                reconciler.reconcile(householdId, lotIds, itemIds, true));

        var lowStockTasks = taskMapper.selectList(null).stream()
                .filter(t -> "LOW_STOCK".equals(t.getKind()))
                .toList();

        // 期望：3 条独立 OPEN 任务，item_id 各不相同
        assertThat(lowStockTasks).hasSize(3);
        assertThat(lowStockTasks).allMatch(t -> "OPEN".equals(t.getStatus()));
        assertThat(lowStockTasks).extracting(TaskEntity::getItemId).containsExactlyInAnyOrderElementsOf(itemIds);
        assertThat(lowStockTasks).allMatch(t -> t.getLotId() == null);
        assertThat(lowStockTasks).allMatch(t -> t.getQtySnapshot() != null && t.getQtySnapshot().compareTo(BigDecimal.ONE) == 0);

        // 每个 item 都应收到一条 TASK_CREATED 通知
        var createdNotifs = notificationMapper.selectList(null).stream()
                .filter(n -> "TASK_CREATED".equals(n.getScope()))
                .toList();
        assertThat(createdNotifs).hasSize(3);
    }
}
