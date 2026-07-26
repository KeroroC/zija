package com.zija.reminder.internal;

import com.zija.catalog.internal.persistence.UnitEntity;
import com.zija.catalog.internal.persistence.UnitMapper;
import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.reminder.internal.persistence.TaskEntity;
import com.zija.reminder.internal.persistence.TaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.*;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class ReminderDashboardIntegrationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean(name = "reminderClock")
    Clock clock;

    @Autowired DashboardService dashboardService;
    @Autowired TaskMapper taskMapper;
    @Autowired HouseholdMapper householdMapper;
    @Autowired UnitMapper unitMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager txManager;

    private UUID householdId, unitId, itemId;
    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDate.of(2026, 7, 26).atStartOfDay(ZoneOffset.UTC).toInstant().atOffset(ZoneOffset.UTC);
        lenient().when(clock.instant()).thenReturn(now.toInstant());
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        jdbc.execute("TRUNCATE TABLE inventory_movement, inventory_stock_position, inventory_lot, reminder_notification, reminder_task, reminder_household_rule, reminder_processed_event, reminder_event_dead_letter, audit_log, catalog_item, catalog_unit, location, household, account RESTART IDENTITY CASCADE");
        // Drop unique partial index to allow seeding multiple LOW_STOCK OPEN tasks per household in tests
        jdbc.execute("DROP INDEX IF EXISTS uq_reminder_task_open");

        var hh = new HouseholdEntity();
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
        itemId = seedItem("基准物品");
    }

    private UUID seedItem(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO catalog_item (id, household_id, name, management_type, unit_id, status, expiry_reminder_mode, low_stock_mode) VALUES (?, ?, ?, 'CONSUMABLE', ?, 'ACTIVE', 'INHERIT', 'DISABLED')",
                id, householdId, name, unitId);
        return id;
    }

    private UUID seedLot() {
        UUID lotId = UUID.randomUUID();
        jdbc.update("INSERT INTO inventory_lot (id, household_id, item_id, lot_number) VALUES (?, ?, ?, ?)",
                lotId, householdId, itemId, "LOT-" + lotId.toString().substring(0, 8));
        return lotId;
    }

    private void seedExpiryTask(int daysLeft, String severity) {
        var t = new TaskEntity();
        t.setId(UUID.randomUUID());
        t.setHouseholdId(householdId);
        t.setKind("EXPIRY");
        t.setLotId(seedLot());
        t.setItemId(itemId);
        t.setStatus("OPEN");
        t.setDueAt(now.plusDays(daysLeft));
        t.setSeverity(severity);
        t.setThresholdSnapshot(Map.of());
        t.setLastReconciledAt(now);
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        t.setVersion(0);
        new TransactionTemplate(txManager).executeWithoutResult(s -> taskMapper.insert(t));
    }

    private void seedLowStockTask(String severity) {
        var t = new TaskEntity();
        t.setId(UUID.randomUUID());
        t.setHouseholdId(householdId);
        t.setKind("LOW_STOCK");
        t.setLotId(null);
        t.setItemId(itemId);
        t.setStatus("OPEN");
        t.setDueAt(now);
        t.setSeverity(severity);
        t.setQtySnapshot(BigDecimal.ZERO);
        t.setThresholdSnapshot(Map.of("threshold", "2"));
        t.setLastReconciledAt(now);
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        t.setVersion(0);
        new TransactionTemplate(txManager).executeWithoutResult(s -> taskMapper.insert(t));
    }

    @Test
    void dashboardReturnsCorrectCountsAndTopN() {
        for (int i = 0; i < 12; i++) seedExpiryTask(i % 7, i == 0 ? "URGENT" : "WARN");
        for (int i = 0; i < 5; i++) seedLowStockTask("INFO");
        // +2 URGENT priority tasks outside the 7-day expiry window
        seedExpiryTask(8, "URGENT");
        seedExpiryTask(8, "URGENT");

        var d = dashboardService.dashboard(householdId, 7, 8);
        assertThat(d.expiryWithin7Days().count()).isEqualTo(12);
        assertThat(d.expiryWithin7Days().items()).hasSize(8);
        assertThat(d.lowStockItems().count()).isEqualTo(5);
        assertThat(d.lowStockItems().items()).hasSize(5);
        assertThat(d.priorityTasks().count()).isEqualTo(19);
        assertThat(d.priorityTasks().items()).hasSize(8);
    }

    @Test
    void daysAndTopNParamsRespected() {
        for (int i = 0; i < 3; i++) seedExpiryTask(i, "INFO");
        var d = dashboardService.dashboard(householdId, 2, 1);
        assertThat(d.expiryWithin7Days().count()).isEqualTo(2);
        assertThat(d.expiryWithin7Days().items()).hasSize(1);
    }
}
