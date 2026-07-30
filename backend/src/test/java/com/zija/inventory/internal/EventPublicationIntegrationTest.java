package com.zija.inventory.internal;

import com.zija.inventory.StockChangedEvent;
import com.zija.inventory.internal.event.InventoryEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.zija.SharedPostgres;

/**
 * 验证 Spring Modulith 2.0.5 事件可靠投递：
 * 事务内 publish 的事件自动登记到 {@code event_publication} 表，
 * 事务提交后由 {@code OrderedTransactionEventPublisher} 派发给监听器。
 */
@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
@Import(EventPublicationIntegrationTest.TestListener.class)
class EventPublicationIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired InventoryEventPublisher publisher;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    /**
     * 测试专用监听器：无实际业务逻辑，仅触发 Spring Modulith 事件登记。
     */
    @TestConfiguration
    static class TestListener {
        @ApplicationModuleListener
        void onStockChanged(StockChangedEvent event) {
            // no-op: just listening to trigger event persistence
        }
    }

    @Test
    void eventRegisteredToEventPublicationTableOnCommit() throws Exception {
        var eventId = UUID.randomUUID();
        var evt = new StockChangedEvent(
                eventId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "INBOUND", BigDecimal.ONE, null, UUID.randomUUID(),
                OffsetDateTime.now(), UUID.randomUUID(), UUID.randomUUID(),
                null, null, null);

        new TransactionTemplate(txManager).executeWithoutResult(s -> publisher.publish(evt));

        // 等待异步派发完成
        Thread.sleep(300);

        // 验证事件已登记到 event_publication 表
        var count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_publication WHERE event_type LIKE '%StockChangedEvent%'",
                Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);

        // 验证事件已派发（completion_date 非空表示监听器已执行）
        var completed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_publication WHERE event_type LIKE '%StockChangedEvent%' AND completion_date IS NOT NULL",
                Integer.class);
        assertThat(completed).isGreaterThanOrEqualTo(1);
    }
}
