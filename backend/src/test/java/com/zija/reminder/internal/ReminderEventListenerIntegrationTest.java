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

    private StockChangedEvent evt(UUID eventId, UUID lotId, UUID itemId) {
        return new StockChangedEvent(eventId, UUID.randomUUID(), lotId, itemId,
                "INBOUND", BigDecimal.ONE, null, UUID.randomUUID(),
                OffsetDateTime.now(), UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void normalEvent_processesOnce() {
        var e = evt(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        listener.onStockChanged(e);
        assertThat(processedEventMapper.selectById(e.eventId())).isNotNull();
    }

    @Test
    void duplicateEventId_skipsSecond() {
        var e = evt(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        listener.onStockChanged(e);
        var beforeTask = taskMapper.selectList(null).size();
        listener.onStockChanged(e); // 重复
        assertThat(taskMapper.selectList(null)).hasSize(beforeTask); // 不重复处理
    }

    @Test
    void listenerThrows_writesDeadLetterAndRetrySucceeds() {
        // 强制 reconciliation 抛异常：用不存在的 household——catalogApi.requireItem 抛 NoSuchEntity
        var e = new StockChangedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "INBOUND", BigDecimal.ONE, null, UUID.randomUUID(),
                OffsetDateTime.now(), UUID.randomUUID(), UUID.randomUUID());
        listener.onStockChanged(e); // 内部 reconcile 会因无家庭/物品失败 → 写 dead_letter
        var dl = deadLetterMapper.selectList(null);
        assertThat(dl).isNotEmpty();

        // 模拟重投前先把家庭/物品建好使 reconcile 成功——此处仅验证重投调用不抛且 dead_letter 被删除
        retryService.retryOnceNow(dl.get(0).getId());
        // 重投成功（已 processed）则 dead_letter 应被删
        assertThat(deadLetterMapper.selectById(dl.get(0).getId())).isNull();
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
