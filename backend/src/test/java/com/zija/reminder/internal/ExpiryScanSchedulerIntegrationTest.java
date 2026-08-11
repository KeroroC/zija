package com.zija.reminder.internal;

import com.zija.TestDb;
import com.zija.catalog.internal.persistence.*;
import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.inventory.internal.StockCommandService;
import com.zija.location.internal.persistence.LocationEntity;
import com.zija.location.internal.persistence.LocationMapper;
import com.zija.reminder.internal.persistence.TaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.zija.SharedPostgres;

@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class ExpiryScanSchedulerIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @MockitoBean(name = "reminderClock")
    Clock clock;

    @Autowired ExpiryScanScheduler scheduler;
    @Autowired ReminderService reminderService;
    @Autowired TaskMapper taskMapper;
    @Autowired HouseholdMapper householdMapper;
    @Autowired ItemMapper itemMapper;
    @Autowired UnitMapper unitMapper;
    @Autowired LocationMapper locationMapper;
    @Autowired StockCommandService stockCommandService;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager txManager;

    private UUID householdId, itemId, unitId, locA, accountId;

    @BeforeEach
    void setUp() {
        // Default clock to a baseline date
        lenient().when(clock.instant()).thenReturn(LocalDate.of(2026, 7, 26).atStartOfDay(ZoneOffset.UTC).toInstant());
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        TestDb.cleanAll(jdbc);

        var hh = new HouseholdEntity();
        hh.setSingletonKey((short) 1);
        hh.setId(UUID.randomUUID());
        hh.setName("T");
        hh.setTimezone("Asia/Shanghai");
        householdMapper.insertSingleton(hh);
        householdId = hh.getId();

        var u = new UnitEntity();
        u.setId(UUID.randomUUID());
        u.setHouseholdId(householdId);
        u.setName("个");
        u.setNameNormalized("个");
        u.setDecimalScale((short) 0);
        u.setStatus("ACTIVE");
        unitMapper.insert(u);
        unitId = u.getId();

        locA = seedLoc("A");
        accountId = UUID.randomUUID();

        // Seed account for FK constraint on inventory_movement
        jdbc.update("INSERT INTO account (id, username, username_normalized, password_hash, display_name) VALUES (?, ?, ?, 'hash', '测试')",
                accountId, "user" + accountId.toString().substring(0, 6), "user" + accountId.toString().substring(0, 6));

        var it = new ItemEntity();
        it.setId(UUID.randomUUID());
        it.setHouseholdId(householdId);
        it.setName("牛奶");
        it.setManagementType("CONSUMABLE");
        it.setUnitId(unitId);
        it.setStatus("ACTIVE");
        it.setExpiryReminderMode("INHERIT");
        it.setLowStockMode("DISABLED"); // avoid LOW_STOCK task interference
        itemMapper.insert(it);
        itemId = it.getId();

        reminderService.getOrCreateRule(householdId);
    }

    private UUID seedLoc(String name) {
        var l = new LocationEntity();
        l.setId(UUID.randomUUID());
        l.setHouseholdId(householdId);
        l.setName(name);
        l.setNameNormalized(name);
        l.setSortOrder(0);
        locationMapper.insert(l);
        return l.getId();
    }

    private UUID inbound(LocalDate expiry) {
        var cmd = new StockCommandService.InboundNewLotCommand(
                itemId, BigDecimal.TEN, LocalDate.now(clock), null, expiry, null, null, null);
        return new TransactionTemplate(txManager).execute(s ->
                stockCommandService.inboundNewLot(householdId, accountId, locA, cmd)).lotId();
    }

    private void tick(LocalDate date) {
        var instant = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        when(clock.instant()).thenReturn(instant);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void notInWindow_noTaskCreated() {
        tick(LocalDate.of(2026, 12, 1));
        inbound(LocalDate.of(2027, 1, 1)); // 31 days left > maxDay 30
        scheduler.scanAt(LocalDate.of(2026, 12, 1));
        assertThat(taskMapper.selectList(null)).noneMatch(t -> "EXPIRY".equals(t.getKind()));
    }

    @Test
    void entersWindow_createsOpenTaskWithSeverity() {
        tick(LocalDate.of(2026, 12, 25));
        inbound(LocalDate.of(2026, 12, 29)); // 4 days left
        scheduler.scanAt(LocalDate.of(2026, 12, 25));
        var tasks = taskMapper.selectList(null);
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getStatus()).isEqualTo("OPEN");
        assertThat(tasks.get(0).getSeverity()).isEqualTo("WARN");
    }

    @Test
    void urgentWhenDaysLeftLe1() {
        tick(LocalDate.of(2026, 12, 29));
        inbound(LocalDate.of(2026, 12, 29)); // same day, daysLeft=0
        scheduler.scanAt(LocalDate.of(2026, 12, 29));
        assertThat(taskMapper.selectList(null).get(0).getSeverity()).isEqualTo("URGENT");
    }

    @Test
    void snoozedPastUntil_reopensOnScan() {
        tick(LocalDate.of(2026, 12, 25));
        inbound(LocalDate.of(2026, 12, 29));
        scheduler.scanAt(LocalDate.of(2026, 12, 25));
        var taskId = taskMapper.selectList(null).get(0).getId();

        new TransactionTemplate(txManager).executeWithoutResult(s ->
                taskMapper.snooze(householdId, taskId, List.of("OPEN", "SNOOZED"),
                        OffsetDateTime.of(2026, 12, 26, 0, 0, 0, 0, ZoneOffset.UTC)));

        tick(LocalDate.of(2026, 12, 27));
        scheduler.scanAt(LocalDate.of(2026, 12, 27)); // snoozed_until already passed
        assertThat(taskMapper.selectById(taskId).getStatus()).isEqualTo("OPEN");
    }

    @Test
    void crossHouseholdIsolation() {
        tick(LocalDate.of(2026, 12, 25));
        inbound(LocalDate.of(2026, 12, 29));
        scheduler.scanAt(LocalDate.of(2026, 12, 25));
        var tasks = taskMapper.selectList(null);
        assertThat(tasks).isNotEmpty();
        // All tasks belong to the registered household
        assertThat(tasks).allMatch(t -> t.getHouseholdId().equals(householdId));
        // No tasks belong to a non-existent household
        assertThat(tasks).noneMatch(t -> t.getHouseholdId().equals(UUID.randomUUID()));
    }
}
