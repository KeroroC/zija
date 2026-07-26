package com.zija.reminder.internal;

import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.reminder.internal.persistence.HouseholdRuleMapper;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class ReminderHouseholdRuleIntegrationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired ReminderService reminderService;
    @Autowired HouseholdRuleMapper ruleMapper;
    @Autowired HouseholdMapper householdMapper;
    @Autowired JdbcTemplate jdbc;

    private UUID householdId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE reminder_notification, reminder_task, reminder_household_rule, reminder_processed_event, reminder_event_dead_letter, audit_log, household, account RESTART IDENTITY CASCADE");
        var hh = new HouseholdEntity(); hh.setSingletonKey((short) 1); hh.setId(UUID.randomUUID()); hh.setName("T"); hh.setTimezone("Asia/Shanghai");
        householdMapper.insertSingleton(hh); householdId = hh.getId();
    }

    @Test
    void firstGetLazilyInitializesWithSpecDefaults() {
        var rule = reminderService.getOrCreateRule(householdId);
        assertThat(rule.expiryDisabled()).isFalse();
        assertThat(rule.expiryReminderDays()).containsExactly((short)30,(short)7,(short)1);
        assertThat(rule.lowStockDisabled()).isFalse();
        assertThat(rule.lowStockThreshold()).isEqualByComparingTo("1");
        // persisted
        var rows = ruleMapper.selectList(null);
        assertThat(rows).hasSize(1);
    }

    @Test
    void putSucceedsWithMatchingVersion() {
        reminderService.getOrCreateRule(householdId);
        var current = reminderService.getOrCreateRule(householdId);
        var updated = reminderService.updateRule(householdId, new ReminderService.RuleUpdate(
                true, List.of((short)60,(short)14,(short)3), true, new BigDecimal("5"), current.version()));
        assertThat(updated.expiryDisabled()).isTrue();
        assertThat(updated.expiryReminderDays()).containsExactly((short)60,(short)14,(short)3);
        assertThat(updated.version()).isEqualTo(current.version() + 1);
    }

    @Test
    void putWithStaleVersionThrowsConflict() {
        reminderService.getOrCreateRule(householdId);
        var v0 = reminderService.getOrCreateRule(householdId);
        reminderService.updateRule(householdId, new ReminderService.RuleUpdate(true, List.of((short)60), true, BigDecimal.TEN, v0.version()));
        assertThatThrownBy(() -> reminderService.updateRule(householdId, new ReminderService.RuleUpdate(false, List.of((short)30,(short)7,(short)1), false, BigDecimal.ONE, v0.version())))
                .isInstanceOf(ReminderRuleVersionConflictException.class);
    }

    @Test
    void putInvalidExpiryDaysThrows() {
        reminderService.getOrCreateRule(householdId);
        var v = reminderService.getOrCreateRule(householdId);
        // not descending
        assertThatThrownBy(() -> reminderService.updateRule(householdId, new ReminderService.RuleUpdate(false, List.of((short)7,(short)30,(short)1), false, BigDecimal.ONE, v.version())))
                .isInstanceOf(ReminderRuleExpiryDaysInvalidException.class);
        // out of range
        assertThatThrownBy(() -> reminderService.updateRule(householdId, new ReminderService.RuleUpdate(false, List.of((short)4000), false, BigDecimal.ONE, v.version())))
                .isInstanceOf(ReminderRuleExpiryDaysInvalidException.class);
        // duplicates
        assertThatThrownBy(() -> reminderService.updateRule(householdId, new ReminderService.RuleUpdate(false, List.of((short)7,(short)7), false, BigDecimal.ONE, v.version())))
                .isInstanceOf(ReminderRuleExpiryDaysInvalidException.class);
    }

    @Test
    void putInvalidLowStockThrows() {
        reminderService.getOrCreateRule(householdId);
        var v = reminderService.getOrCreateRule(householdId);
        // zero
        assertThatThrownBy(() -> reminderService.updateRule(householdId, new ReminderService.RuleUpdate(false, List.of((short)30,(short)7,(short)1), false, new BigDecimal("0"), v.version())))
                .isInstanceOf(ReminderRuleLowStockInvalidException.class);
        // negative
        assertThatThrownBy(() -> reminderService.updateRule(householdId, new ReminderService.RuleUpdate(false, List.of((short)30,(short)7,(short)1), false, new BigDecimal("-1"), v.version())))
                .isInstanceOf(ReminderRuleLowStockInvalidException.class);
    }

    @Test
    void putWritesRuleChangedNotificationAndAudit() {
        reminderService.getOrCreateRule(householdId);
        var v = reminderService.getOrCreateRule(householdId);
        reminderService.updateRule(householdId, new ReminderService.RuleUpdate(true, List.of((short)60), true, BigDecimal.TEN, v.version()));
        var notifs = jdbc.queryForList("SELECT scope FROM reminder_notification WHERE household_id = ?", householdId);
        assertThat(notifs).anyMatch(row -> "RULE_CHANGED".equals(row.get("scope")));
        var audits = jdbc.queryForList("SELECT action FROM audit_log WHERE household_id = ?", householdId);
        assertThat(audits).anyMatch(row -> "REMINDER_RULE_UPDATE".equals(row.get("action")));
    }
}
