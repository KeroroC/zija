package com.zija.reporting.internal.projection;

import com.zija.inventory.StockChangedEvent;
import com.zija.reporting.internal.persistence.DeadLetterEntity;
import com.zija.reporting.internal.persistence.ReportingDeadLetterMapper;
import com.zija.reporting.internal.persistence.ReportingProcessedEventMapper;
import com.zija.reporting.internal.persistence.SearchIndexMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.zija.SharedPostgres;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 覆盖 {@link ReportingEventRetryService} 的死信重投路径。
 * 这些测试在修复前会失败：原 listener 自吞异常 → retry 永远走不到 catch →
 * deleteById 总是执行 → 死信被静默删除（甚至被轮换为新 id）。
 */
@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class ReportingEventRetryServiceTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired ProjectionListener listener;
    @Autowired ReportingEventRetryService retryService;
    @Autowired ReportingDeadLetterMapper deadLetterMapper;
    @Autowired ReportingProcessedEventMapper processedEventMapper;
    @Autowired SearchIndexMapper searchIndexMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE reporting_movement_flat, reporting_stock_flat, reporting_search_index, "
                + "reporting_event_dead_letter, reporting_processed_event, "
                + "audit_log, inventory_movement, inventory_stock_position, inventory_lot, "
                + "location, catalog_item, catalog_unit, catalog_category, member, household, account "
                + "RESTART IDENTITY CASCADE");
    }

    private StockChangedEvent stockEvent(UUID householdId, UUID itemId) {
        return new StockChangedEvent(UUID.randomUUID(), householdId, UUID.randomUUID(), itemId,
                "INBOUND", BigDecimal.ONE, null, UUID.randomUUID(),
                OffsetDateTime.now(), UUID.randomUUID(), UUID.randomUUID(),
                null, null, null);
    }

    /**
     * 失败重试：死信应被原样保留，failureCount 递增，lastError 设置。
     * 修复前：listener catch 写新死信 → retry 删旧死信 → 看似"成功" → 死信轮换、failureCount=1。
     */
    @Test
    void stockChanged_retryWhileFailing_keepsDeadLetterAndIncrementsFailureCount() {
        // 不 seed 任何 catalog_item → catalogApi.requireItem 抛 NoSuchEntity → 投影失败
        UUID householdId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        var e = stockEvent(householdId, itemId);
        listener.onStockChanged(e);
        var initial = deadLetterMapper.selectList(null).get(0);
        UUID originalId = initial.getId();
        OffsetDateTime originalNextRetryAt = initial.getNextRetryAt();
        assertThat(initial.getFailureCount()).isEqualTo(1);

        retryService.retryOnceNow(originalId);

        var afterFirst = deadLetterMapper.selectById(originalId);
        assertThat(afterFirst).as("死信 id 不应轮换").isNotNull();
        assertThat(afterFirst.getId()).isEqualTo(originalId);
        assertThat(afterFirst.getFailureCount()).isEqualTo(2);
        assertThat(afterFirst.getLastError()).isNotBlank();
        assertThat(afterFirst.getNextRetryAt())
                .as("nextRetryAt 应按指数退避后移")
                .isAfter(originalNextRetryAt);

        // 再次重试，仍失败 → failureCount=3
        retryService.retryOnceNow(originalId);
        assertThat(deadLetterMapper.selectById(originalId).getFailureCount()).isEqualTo(3);
    }

    /**
     * 走真实 retryOne 循环至 abandoned：第 9 次（newCount=10）触发 markAbandoned
     * + 写 REPORTING_EVENT_ABANDONED 审计。
     * 修复前 listener 一直吞异常 → 永远停在新死信 + failureCount=1。
     *
     * 注意 markAbandoned 不再 bump failureCount（仅设 abandoned=true），
     * 所以循环结束时 db.failureCount 是 9（最后一次 increment 的结果），不是 10。
     */
    @Test
    void retryUntilAbandoned_writesReportingEventAbandonedAudit() {
        UUID householdId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        var e = stockEvent(householdId, itemId);
        listener.onStockChanged(e);
        var dl = deadLetterMapper.selectList(null).get(0);

        // 9 次重试（仍无 item → 仍失败），第 9 次时 newCount=10 触发 markAbandoned
        for (int i = 0; i < 9; i++) {
            retryService.retryOnceNow(dl.getId());
        }

        var finalDl = deadLetterMapper.selectById(dl.getId());
        assertThat(finalDl).isNotNull();
        assertThat(finalDl.getAbandoned()).isTrue();
        assertThat(finalDl.getFailureCount())
                .as("累计 9 次 increment 后 failureCount=9；markAbandoned 不再 bump")
                .isEqualTo(9);
        var audits = jdbc.queryForList("SELECT action, outcome FROM audit_log");
        assertThat(audits).anyMatch(r -> "REPORTING_EVENT_ABANDONED".equals(r.get("action"))
                && "FAILURE".equals(r.get("outcome")));
    }
}
