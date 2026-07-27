package com.zija.reminder.internal;

import com.zija.inventory.StockChangedEvent;
import com.zija.reminder.internal.persistence.DeadLetterEntity;
import com.zija.reminder.internal.persistence.DeadLetterMapper;
import com.zija.system.SystemApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Dead-letter 定时重投服务。每隔 30 秒扫描到期条目，按指数退避重试，
 * 超过最大失败次数（10）后标记为 abandoned 并写 poison 审计。
 */
@Service
public class EventRetryService {

    private static final Logger log = LoggerFactory.getLogger(EventRetryService.class);
    private static final int MAX_FAILURES = 10;

    private final DeadLetterMapper deadLetterMapper;
    private final ReminderEventListener listener;
    private final SystemApi systemApi;

    public EventRetryService(DeadLetterMapper deadLetterMapper,
                              ReminderEventListener listener,
                              SystemApi systemApi) {
        this.deadLetterMapper = deadLetterMapper;
        this.listener = listener;
        this.systemApi = systemApi;
    }

    @Scheduled(fixedDelay = 30_000)
    public void retryPending() {
        var due = deadLetterMapper.findDueForRetry(OffsetDateTime.now(), 50);
        for (var dl : due) {
            retryOne(dl);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryOnceNow(UUID dlId) {
        var dl = deadLetterMapper.selectById(dlId);
        if (dl != null) retryOne(dl);
    }

    /**
     * 测试辅助：强制将 failureCount 推到 MAX_FAILURES 并标记 abandoned + 写 poison 审计。
     */
    void forceFailAndRetryUntilAbandoned(UUID dlId) {
        var dl = deadLetterMapper.selectById(dlId);
        if (dl == null) return;
        // 将 failureCount 推到 MAX_FAILURES
        while (dl.getFailureCount() < MAX_FAILURES) {
            deadLetterMapper.incrementFailure(dlId,
                    OffsetDateTime.now().plusSeconds(30),
                    "forced fail");
            dl.setFailureCount(dl.getFailureCount() + 1);
        }
        deadLetterMapper.markAbandoned(dlId);
        systemApi.recordAudit(new SystemApi.AuditEvent(
                "REMINDER_EVENT_POISON", "FAILURE", null, null, null, null, null,
                Map.of("eventId", dl.getEventId().toString())));
    }

    private void retryOne(DeadLetterEntity dl) {
        try {
            var evt = fromMap(dl.getPayload());
            listener.onStockChanged(evt);
            // 成功：删除 dead-letter
            deadLetterMapper.deleteById(dl.getId());
        } catch (RuntimeException ex) {
            int newCount = dl.getFailureCount() + 1;
            if (newCount >= MAX_FAILURES) {
                deadLetterMapper.markAbandoned(dl.getId());
                systemApi.recordAudit(new SystemApi.AuditEvent(
                        "REMINDER_EVENT_POISON", "FAILURE", null, null, null, null, null,
                        Map.of("eventId", dl.getEventId().toString())));
                log.warn("Dead-letter abandoned after {} failures: eventId={}", newCount, dl.getEventId());
            } else {
                // 指数退避：30s * 2^count（上限 2^6 = 64x = 1920s ≈ 32min）
                long backoffSeconds = 30L * (1L << Math.min(newCount, 6));
                deadLetterMapper.incrementFailure(dl.getId(),
                        OffsetDateTime.now().plus(Duration.ofSeconds(backoffSeconds)),
                        truncate(ex.getMessage(), 4000));
            }
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "UnknownError";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private StockChangedEvent fromMap(Map<String, Object> m) {
        return new StockChangedEvent(
                UUID.fromString((String) m.get("eventId")),
                UUID.fromString((String) m.get("householdId")),
                UUID.fromString((String) m.get("lotId")),
                UUID.fromString((String) m.get("itemId")),
                (String) m.get("movementType"),
                new BigDecimal((String) m.get("quantityDelta")),
                ((String) m.get("fromLocationId")).isEmpty() ? null : UUID.fromString((String) m.get("fromLocationId")),
                ((String) m.get("toLocationId")).isEmpty() ? null : UUID.fromString((String) m.get("toLocationId")),
                OffsetDateTime.parse((String) m.get("businessTime")),
                UUID.fromString((String) m.get("movementId")),
                UUID.fromString((String) m.get("idempotencyKey")),
                // 阶段六追加字段，旧 dead-letter payload 缺键时容错取 null:
                m.containsKey("operatorAccountId") && !((String) m.get("operatorAccountId")).isEmpty()
                        ? UUID.fromString((String) m.get("operatorAccountId")) : null,
                m.containsKey("reason") ? (String) m.get("reason") : null,
                m.containsKey("reversalOf") && !((String) m.get("reversalOf")).isEmpty()
                        ? UUID.fromString((String) m.get("reversalOf")) : null
        );
    }
}
