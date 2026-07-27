package com.zija.reporting.internal.projection;

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

    @Scheduled(fixedDelay = 30_000)
    public void retryPending() {
        var due = deadLetterMapper.findDueForRetry(OffsetDateTime.now(), 50);
        for (var dl : due) {
            retryOne(dl);
        }
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
                        "REPORTING_EVENT_ABANDONED", "FAILURE", null, null, null, null, null,
                        Map.of("eventId", dl.getEventId().toString(),
                               "eventType", dl.getEventType())));
                log.warn("Reporting dead-letter abandoned after {} failures: eventId={}",
                        newCount, dl.getEventId());
            } else {
                long backoffSeconds = 30L * (1L << Math.min(newCount, 6));
                deadLetterMapper.incrementFailure(dl.getId(),
                        OffsetDateTime.now().plus(Duration.ofSeconds(backoffSeconds)),
                        truncate(ex.getMessage(), 4000));
            }
        }
    }

    /**
     * 按 eventType 分派到 ProjectionListener 对应的事件处理方法。
     */
    private void dispatchToListener(DeadLetterEntity dl) {
        Map<String, Object> payload = dl.getPayload();
        String eventType = dl.getEventType();

        switch (eventType) {
            case "StockChangedEvent" -> listener.onStockChanged(fromStockChangedMap(payload));
            case "ItemChangedEvent" -> listener.onItemChanged(fromItemChangedMap(payload));
            case "CategoryChangedEvent" -> listener.onCategoryChanged(fromCategoryChangedMap(payload));
            case "BrandChangedEvent" -> listener.onBrandChanged(fromBrandChangedMap(payload));
            case "UnitChangedEvent" -> listener.onUnitChanged(fromUnitChangedMap(payload));
            case "TagChangedEvent" -> listener.onTagChanged(fromTagChangedMap(payload));
            case "LocationChangedEvent" -> listener.onLocationChanged(fromLocationChangedMap(payload));
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
        if (s == null) return "UnknownError";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
