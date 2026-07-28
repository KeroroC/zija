package com.zija.reminder.internal;

import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.reminder.internal.exception.ReminderTaskInvalidTransitionException;
import com.zija.reminder.internal.exception.ReminderTaskNotFoundException;
import com.zija.reminder.internal.exception.ReminderTaskSnoozeUntilInvalidException;
import com.zija.reminder.internal.persistence.TaskEntity;
import com.zija.reminder.internal.persistence.TaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class ReminderTaskStateIntegrationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired ReminderTaskStateService stateService;
    @Autowired TaskMapper taskMapper;
    @Autowired HouseholdMapper householdMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager txManager;

    private UUID householdId, itemId, taskId;

    private void seedTask(String status) {
        var hh = new HouseholdEntity();
        hh.setSingletonKey((short) 1);
        hh.setId(UUID.randomUUID());
        hh.setName("T");
        hh.setTimezone("Asia/Shanghai");
        householdMapper.insertSingleton(hh);
        householdId = hh.getId();

        // Seed FK dependencies via JDBC
        UUID unitId = UUID.randomUUID();
        jdbc.update("INSERT INTO catalog_unit (id, household_id, name, name_normalized, decimal_scale) VALUES (?, ?, ?, ?, ?)",
                unitId, householdId, "个", "个", 0);
        itemId = UUID.randomUUID();
        jdbc.update("INSERT INTO catalog_item (id, household_id, name, management_type, unit_id) VALUES (?, ?, ?, ?, ?)",
                itemId, householdId, "测试物品", "CONSUMABLE", unitId);

        // Use LOW_STOCK kind (no lot_id FK needed)
        var t = new TaskEntity();
        t.setId(UUID.randomUUID());
        t.setHouseholdId(householdId);
        t.setKind("LOW_STOCK");
        t.setLotId(null);
        t.setItemId(itemId);
        t.setStatus(status);
        t.setDueAt(OffsetDateTime.now().plusDays(3));
        t.setSeverity("WARN");
        t.setThresholdSnapshot(Map.of("threshold", "1"));
        t.setLastReconciledAt(OffsetDateTime.now());
        t.setCreatedAt(OffsetDateTime.now());
        t.setUpdatedAt(OffsetDateTime.now());
        t.setVersion(0);
        new TransactionTemplate(txManager).executeWithoutResult(s -> taskMapper.insert(t));
        taskId = t.getId();
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE reminder_notification, reminder_task, reminder_household_rule, reminder_processed_event, reminder_event_dead_letter, audit_log, catalog_item, catalog_unit, location, household, account RESTART IDENTITY CASCADE");
    }

    @Test
    void snoozeFromOpenSucceeds() {
        seedTask("OPEN");
        var until = OffsetDateTime.now().plusDays(1);
        stateService.snooze(householdId, taskId, until);
        var updated = taskMapper.selectById(taskId);
        assertThat(updated.getStatus()).isEqualTo("SNOOZED");
        assertThat(updated.getSnoozedUntil()).isNotNull();
        var audits = jdbc.queryForList("SELECT action FROM audit_log WHERE household_id = ?", householdId);
        assertThat(audits).anyMatch(row -> "REMINDER_TASK_SNOOZED".equals(row.get("action")));
    }

    @Test
    void snoozeFromSnoozedSucceeds() {
        seedTask("SNOOZED");
        stateService.snooze(householdId, taskId, OffsetDateTime.now().plusDays(2));
        assertThat(taskMapper.selectById(taskId).getStatus()).isEqualTo("SNOOZED");
    }

    @Test
    void snoozeFromDoneThrowsTransition() {
        seedTask("DONE");
        assertThatThrownBy(() -> stateService.snooze(householdId, taskId, OffsetDateTime.now().plusDays(1)))
                .isInstanceOf(ReminderTaskInvalidTransitionException.class);
    }

    @Test
    void snoozeUntilInPastThrows() {
        seedTask("OPEN");
        assertThatThrownBy(() -> stateService.snooze(householdId, taskId, OffsetDateTime.now().minusMinutes(5)))
                .isInstanceOf(ReminderTaskSnoozeUntilInvalidException.class);
    }

    @Test
    void completeFromOpenSucceeds() {
        seedTask("OPEN");
        stateService.complete(householdId, taskId);
        var updated = taskMapper.selectById(taskId);
        assertThat(updated.getStatus()).isEqualTo("DONE");
        assertThat(updated.getSnoozedUntil()).isNull();
        var audits = jdbc.queryForList("SELECT action FROM audit_log WHERE household_id = ?", householdId);
        assertThat(audits).anyMatch(row -> "REMINDER_TASK_COMPLETED".equals(row.get("action")));
    }

    @Test
    void completeFromSnoozedSucceeds() {
        seedTask("SNOOZED");
        stateService.complete(householdId, taskId);
        assertThat(taskMapper.selectById(taskId).getStatus()).isEqualTo("DONE");
    }

    @Test
    void completeFromDoneThrowsTransition() {
        seedTask("DONE");
        assertThatThrownBy(() -> stateService.complete(householdId, taskId))
                .isInstanceOf(ReminderTaskInvalidTransitionException.class);
    }

    @Test
    void ignoreFromOpenSucceeds() {
        seedTask("OPEN");
        stateService.ignore(householdId, taskId);
        assertThat(taskMapper.selectById(taskId).getStatus()).isEqualTo("IGNORED");
        var audits = jdbc.queryForList("SELECT action FROM audit_log WHERE household_id = ?", householdId);
        assertThat(audits).anyMatch(row -> "REMINDER_TASK_IGNORED".equals(row.get("action")));
    }

    @Test
    void reopenFromIgnoreSucceeds() {
        seedTask("IGNORED");
        stateService.reopen(householdId, taskId);
        var updated = taskMapper.selectById(taskId);
        assertThat(updated.getStatus()).isEqualTo("OPEN");
        assertThat(updated.getSnoozedUntil()).isNull();
        var audits = jdbc.queryForList("SELECT action FROM audit_log WHERE household_id = ?", householdId);
        assertThat(audits).anyMatch(row -> "REMINDER_TASK_REOPENED".equals(row.get("action")));
    }

    @Test
    void reopenFromDoneSucceeds() {
        seedTask("DONE");
        stateService.reopen(householdId, taskId);
        assertThat(taskMapper.selectById(taskId).getStatus()).isEqualTo("OPEN");
    }

    @Test
    void reopenFromOpenThrowsTransition() {
        seedTask("OPEN");
        assertThatThrownBy(() -> stateService.reopen(householdId, taskId))
                .isInstanceOf(ReminderTaskInvalidTransitionException.class);
    }

    @Test
    void crossHouseholdTaskThrowsNotFound() {
        seedTask("OPEN");
        assertThatThrownBy(() -> stateService.complete(UUID.randomUUID(), taskId))
                .isInstanceOf(ReminderTaskNotFoundException.class);
    }
}
