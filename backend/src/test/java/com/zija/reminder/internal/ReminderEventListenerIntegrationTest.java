package com.zija.reminder.internal;

import com.zija.inventory.StockChangedEvent;
import com.zija.reminder.internal.persistence.*;
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
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class ReminderEventListenerIntegrationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired ReminderEventListener listener;
    @Autowired EventRetryService retryService;
    @Autowired ProcessedEventMapper processedEventMapper;
    @Autowired DeadLetterMapper deadLetterMapper;
    @Autowired TaskMapper taskMapper;
    @Autowired NotificationMapper notificationMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE reminder_notification, reminder_task, reminder_household_rule, reminder_processed_event, reminder_event_dead_letter, audit_log, household, account RESTART IDENTITY CASCADE");
    }

    private StockChangedEvent evt(UUID eventId, UUID householdId, UUID lotId, UUID itemId) {
        return new StockChangedEvent(eventId, householdId, lotId, itemId,
                "INBOUND", BigDecimal.ONE, null, UUID.randomUUID(),
                OffsetDateTime.now(), UUID.randomUUID(), UUID.randomUUID(),
                null, null, null);
    }

    /** 向数据库插入家庭/单位/物品，使 reconciler 不会因缺数据而失败。 */
    private void seedHouseholdAndItem(UUID householdId, UUID itemId) {
        UUID unitId = UUID.randomUUID();
        jdbc.update("INSERT INTO household (id, name, timezone) VALUES (?, ?, ?)",
                householdId, "测试家庭", "Asia/Shanghai");
        jdbc.update("INSERT INTO catalog_unit (id, household_id, name, name_normalized, decimal_scale) VALUES (?, ?, ?, ?, ?)",
                unitId, householdId, "个", "个", 0);
        jdbc.update("INSERT INTO catalog_item (id, household_id, name, management_type, unit_id) VALUES (?, ?, ?, ?, ?)",
                itemId, householdId, "测试物品", "CONSUMABLE", unitId);
    }

    @Test
    void normalEvent_processesOnce() {
        UUID householdId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        seedHouseholdAndItem(householdId, itemId);
        var e = evt(UUID.randomUUID(), householdId, UUID.randomUUID(), itemId);
        listener.onStockChanged(e);
        assertThat(processedEventMapper.selectById(e.eventId())).isNotNull();
    }

    @Test
    void duplicateEventId_skipsSecond() {
        UUID householdId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        seedHouseholdAndItem(householdId, itemId);
        var e = evt(UUID.randomUUID(), householdId, UUID.randomUUID(), itemId);
        listener.onStockChanged(e);
        var beforeTask = taskMapper.selectList(null).size();
        listener.onStockChanged(e); // 重复
        assertThat(taskMapper.selectList(null)).hasSize(beforeTask); // 不重复处理
    }

    @Test
    void listenerThrows_writesDeadLetterAndRetrySucceeds() {
        // 强制 reconciliation 抛异常：用不存在的 household——catalogApi.requireItem 抛 NoSuchEntity
        UUID householdId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        var e = new StockChangedEvent(UUID.randomUUID(), householdId, lotId, itemId,
                "INBOUND", BigDecimal.ONE, null, UUID.randomUUID(),
                OffsetDateTime.now(), UUID.randomUUID(), UUID.randomUUID(),
                null, null, null);
        listener.onStockChanged(e); // 内部 reconcile 会因无家庭/物品失败 → 写 dead_letter
        var dl = deadLetterMapper.selectList(null);
        assertThat(dl).isNotEmpty();
        // 去重行应已删除，允许重试时重新处理
        assertThat(processedEventMapper.selectById(e.eventId())).isNull();

        // 建好家庭/物品使 reconcile 成功
        seedHouseholdAndItem(householdId, itemId);

        retryService.retryOnceNow(dl.get(0).getId());
        // 重投成功则 dead_letter 应被删
        assertThat(deadLetterMapper.selectById(dl.get(0).getId())).isNull();
        // 验证 reconciliation 实际执行（库存为 0 低于默认阈值 1 → 应产生 LOW_STOCK 任务）
        var tasks = taskMapper.selectList(null);
        assertThat(tasks).anyMatch(t -> "LOW_STOCK".equals(t.getKind()) && householdId.equals(t.getHouseholdId()));
    }

    @Test
    void overThresholdRetries_marksAbandonedAndAuditsPoison() {
        var dl = new DeadLetterEntity();
        dl.setId(UUID.randomUUID()); dl.setEventId(UUID.randomUUID());
        dl.setPayload(java.util.Map.of("eventId", "x")); dl.setFailureCount(9);
        dl.setNextRetryAt(OffsetDateTime.now().minusMinutes(1));
        dl.setAbandoned(false); dl.setCreatedAt(OffsetDateTime.now());
        deadLetterMapper.insert(dl);
        retryService.forceFailAndRetryUntilAbandoned(dl.getId());
        assertThat(deadLetterMapper.selectById(dl.getId()).getAbandoned()).isTrue();
        var audits = jdbc.queryForList("SELECT action FROM audit_log");
        assertThat(audits).anyMatch(r -> "REMINDER_EVENT_POISON".equals(r.get("action")));
    }
}
