package com.zija.reporting.internal.projection;

import com.zija.shared.ZijaAuditOutcome;
import com.zija.shared.ZijaErrorCodes;
import com.zija.catalog.*;
import com.zija.inventory.StockChangedEvent;
import com.zija.location.LocationChangedEvent;
import com.zija.reporting.internal.persistence.DeadLetterEntity;
import com.zija.reporting.internal.persistence.ReportingDeadLetterMapper;
import com.zija.system.SystemApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * reporting dead-letter 定时重投服务。
 * 每隔 30 秒扫描到期条目，按指数退避重试，超过 10 次标记 abandoned 并写审计。
 */
@Service
public class ReportingEventRetryService {

    private static final Logger log = LoggerFactory.getLogger(ReportingEventRetryService.class);
    private static final int MAX_FAILURES = 10;
    private static final long RETRY_DELAY_SECONDS = 30;
    private static final int MAX_BACKOFF_SHIFT = 6;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 4000;

    private final ReportingDeadLetterMapper deadLetterMapper;
    private final ProjectionListener listener;
    private final SystemApi systemApi;

    public ReportingEventRetryService(ReportingDeadLetterMapper deadLetterMapper,
                                       ProjectionListener listener,
                                       SystemApi systemApi) {
        this.deadLetterMapper = deadLetterMapper;
        this.listener = listener;
        this.systemApi = systemApi;
    }

    /**
     * 每 30 秒扫描到期死信重投。用 cron 而非 fixedDelay，是为了支持通过
     * {@code zija.schedule.reporting-dead-letter-retry=-} 外部禁用（集成测试必须禁用：
     * 后台写库会与各测试类的 TRUNCATE 形成锁顺序反转导致 PostgreSQL 死锁）。
     */
    @Scheduled(cron = "${zija.schedule.reporting-dead-letter-retry:*/30 * * * * *}",
               zone = "${zija.schedule.zone:Asia/Shanghai}")
    public void retryPending() {
        var due = deadLetterMapper.findDueForRetry(OffsetDateTime.now(), 50);
        for (var dl : due) {
            retryOne(dl);
        }
    }

    /**
     * 测试 / 运维辅助：手动重投一条指定死信。
     * 与提醒模块的 {@code EventRetryService#retryOnceNow} 对称。
     */
    public void retryOnceNow(UUID dlId) {
        var dl = deadLetterMapper.selectById(dlId);
        if (dl != null) retryOne(dl);
    }

    private void retryOne(DeadLetterEntity dl) {
        try {
            dispatchToListener(dl);
            deadLetterMapper.deleteById(dl.getId());
        } catch (RuntimeException ex) {
            int newCount = dl.getFailureCount() + 1;
            if (newCount >= MAX_FAILURES) {
                deadLetterMapper.markAbandoned(dl.getId());
                systemApi.recordAudit(new SystemApi.AuditEvent(
                        SystemApi.AuditAction.REPORTING_EVENT_ABANDONED, ZijaAuditOutcome.FAILURE, null, null, null, null, null,
                        Map.of("eventId", dl.getEventId().toString(),
                               "eventType", dl.getEventType())));
                log.warn("Reporting dead-letter abandoned after {} failures: eventId={}",
                        newCount, dl.getEventId());
            } else {
                long backoffSeconds = RETRY_DELAY_SECONDS * (1L << Math.min(newCount, MAX_BACKOFF_SHIFT));
                deadLetterMapper.incrementFailure(dl.getId(),
                        OffsetDateTime.now().plus(Duration.ofSeconds(backoffSeconds)),
                        truncate(ex.getMessage(), MAX_ERROR_MESSAGE_LENGTH));
            }
        }
    }

    /**
     * 按 eventType 分派到 ProjectionListener 对应的事件处理方法。
     * 调 processXxxEvent 而非 onXxx：监听器的 try/catch 会吞掉异常，
     * 导致 retryOne 的 catch 永远进不去、deleteById 总是执行、死信被静默删除。
     */
    private void dispatchToListener(DeadLetterEntity dl) {
        Map<String, Object> payload = dl.getPayload();
        String eventType = dl.getEventType();

        switch (eventType) {
            case "StockChangedEvent" -> listener.processStockChangedEvent(fromStockChangedMap(payload));
            case "ItemChangedEvent" -> listener.processItemChangedEvent(fromItemChangedMap(payload));
            case "CategoryChangedEvent" -> listener.processCategoryChangedEvent(fromCategoryChangedMap(payload));
            case "BrandChangedEvent" -> listener.processBrandChangedEvent(fromBrandChangedMap(payload));
            case "UnitChangedEvent" -> listener.processUnitChangedEvent(fromUnitChangedMap(payload));
            case "TagChangedEvent" -> listener.processTagChangedEvent(fromTagChangedMap(payload));
            case "LocationChangedEvent" -> listener.processLocationChangedEvent(fromLocationChangedMap(payload));
            default -> log.warn("Unknown event type in dead-letter: {}", eventType);
        }
    }

    // --- 反序列化方法 ---

    private StockChangedEvent fromStockChangedMap(Map<String, Object> m) {
        return new StockChangedEvent(
                UUID.fromString((String) m.get("eventId")),
                UUID.fromString((String) m.get("householdId")),
                UUID.fromString((String) m.get("lotId")),
                UUID.fromString((String) m.get("itemId")),
                (String) m.get("movementType"),
                new BigDecimal((String) m.get("quantityDelta")),
                parseNullableUuid((String) m.get("fromLocationId")),
                parseNullableUuid((String) m.get("toLocationId")),
                OffsetDateTime.parse((String) m.get("businessTime")),
                UUID.fromString((String) m.get("movementId")),
                UUID.fromString((String) m.get("idempotencyKey")),
                parseNullableUuid((String) m.get("operatorAccountId")),
                (String) m.get("reason"),
                parseNullableUuid((String) m.get("reversalOf"))
        );
    }

    private ItemChangedEvent fromItemChangedMap(Map<String, Object> m) {
        return new ItemChangedEvent(
                UUID.fromString((String) m.get("eventId")),
                UUID.fromString((String) m.get("householdId")),
                UUID.fromString((String) m.get("itemId")),
                (String) m.get("changeType"),
                m.containsKey("businessTime")
                        ? OffsetDateTime.parse((String) m.get("businessTime"))
                        : OffsetDateTime.now()
        );
    }

    private CategoryChangedEvent fromCategoryChangedMap(Map<String, Object> m) {
        return new CategoryChangedEvent(
                UUID.fromString((String) m.get("eventId")),
                UUID.fromString((String) m.get("householdId")),
                UUID.fromString((String) m.get("categoryId")),
                (String) m.get("changeType")
        );
    }

    private BrandChangedEvent fromBrandChangedMap(Map<String, Object> m) {
        return new BrandChangedEvent(
                UUID.fromString((String) m.get("eventId")),
                UUID.fromString((String) m.get("householdId")),
                UUID.fromString((String) m.get("brandId")),
                (String) m.get("changeType")
        );
    }

    private UnitChangedEvent fromUnitChangedMap(Map<String, Object> m) {
        return new UnitChangedEvent(
                UUID.fromString((String) m.get("eventId")),
                UUID.fromString((String) m.get("householdId")),
                UUID.fromString((String) m.get("unitId")),
                (String) m.get("changeType")
        );
    }

    private TagChangedEvent fromTagChangedMap(Map<String, Object> m) {
        return new TagChangedEvent(
                UUID.fromString((String) m.get("eventId")),
                UUID.fromString((String) m.get("householdId")),
                UUID.fromString((String) m.get("tagId")),
                (String) m.get("changeType")
        );
    }

    private LocationChangedEvent fromLocationChangedMap(Map<String, Object> m) {
        return new LocationChangedEvent(
                UUID.fromString((String) m.get("eventId")),
                UUID.fromString((String) m.get("householdId")),
                UUID.fromString((String) m.get("locationId")),
                (String) m.get("changeType"),
                parseNullableUuid((String) m.get("parentId")),
                OffsetDateTime.now()
        );
    }

    private static UUID parseNullableUuid(String s) {
        if (s == null || s.isEmpty()) return null;
        return UUID.fromString(s);
    }

    private static String truncate(String s, int max) {
        if (s == null) return ZijaErrorCodes.UNKNOWN_ERROR;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
