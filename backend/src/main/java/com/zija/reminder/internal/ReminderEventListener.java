package com.zija.reminder.internal;

import com.zija.inventory.StockChangedEvent;
import com.zija.reminder.internal.persistence.DeadLetterEntity;
import com.zija.reminder.internal.persistence.DeadLetterMapper;
import com.zija.reminder.internal.persistence.ProcessedEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 监听库存变更事件，去重后调用 reconciler 重算提醒任务。
 * 失败时写 dead-letter（独立事务提交），不向上抛异常。
 */
@Service
public class ReminderEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReminderEventListener.class);

    private final ProcessedEventMapper processedEventMapper;
    private final DeadLetterMapper deadLetterMapper;
    private final ReminderReconciler reconciler;
    private final TransactionTemplate requiresNewTx;

    public ReminderEventListener(ProcessedEventMapper processedEventMapper,
                                  DeadLetterMapper deadLetterMapper,
                                  ReminderReconciler reconciler,
                                  PlatformTransactionManager txManager) {
        this.processedEventMapper = processedEventMapper;
        this.deadLetterMapper = deadLetterMapper;
        this.reconciler = reconciler;
        this.requiresNewTx = new TransactionTemplate(txManager);
        this.requiresNewTx.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
    }

    @EventListener
    public void onStockChanged(StockChangedEvent evt) {
        int rows = processedEventMapper.insertOnConflictDoNothing(evt.eventId());
        if (rows == 0) return; // 已处理，跳过
        try {
            // 在独立事务中调用 reconciler，避免失败标记外层事务为 rollback-only
            reconcilerInNewTx(evt);
        } catch (RuntimeException ex) {
            // 用独立事务写 dead-letter，确保主事务回滚后仍保留
            saveDeadLetterInNewTx(evt, ex);
            log.warn("StockChangedEvent reconcile failed, wrote dead-letter: eventId={}", evt.eventId(), ex);
        }
    }

    private void reconcilerInNewTx(StockChangedEvent evt) {
        requiresNewTx.executeWithoutResult(status -> {
            reconciler.reconcile(evt.householdId(), List.of(evt.lotId()), List.of(evt.itemId()), false);
        });
    }

    private void saveDeadLetterInNewTx(StockChangedEvent evt, Throwable err) {
        requiresNewTx.executeWithoutResult(status -> {
            // 删除去重行，允许重试时重新处理
            processedEventMapper.deleteById(evt.eventId());
            var dl = new DeadLetterEntity();
            dl.setId(UUID.randomUUID());
            dl.setEventId(evt.eventId());
            dl.setPayload(toMap(evt));
            dl.setFailureCount(1);
            dl.setNextRetryAt(OffsetDateTime.now().plusSeconds(30));
            dl.setLastError(truncate(err.getMessage(), 4000));
            dl.setAbandoned(false);
            dl.setCreatedAt(OffsetDateTime.now());
            try {
                deadLetterMapper.insert(dl);
            } catch (org.springframework.dao.DuplicateKeyException ignored) {
                // 并发写入，忽略
            }
        });
    }

    private static String truncate(String s, int max) {
        if (s == null) return "UnknownError";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private Map<String, Object> toMap(StockChangedEvent evt) {
        return Map.ofEntries(
                Map.entry("eventId", evt.eventId().toString()),
                Map.entry("householdId", evt.householdId().toString()),
                Map.entry("lotId", evt.lotId().toString()),
                Map.entry("itemId", evt.itemId().toString()),
                Map.entry("movementType", evt.movementType()),
                Map.entry("quantityDelta", evt.quantityDelta().toString()),
                Map.entry("fromLocationId", evt.fromLocationId() == null ? "" : evt.fromLocationId().toString()),
                Map.entry("toLocationId", evt.toLocationId() == null ? "" : evt.toLocationId().toString()),
                Map.entry("businessTime", evt.businessTime().toString()),
                Map.entry("movementId", evt.movementId().toString()),
                Map.entry("idempotencyKey", evt.idempotencyKey().toString()),
                // 阶段六追加字段：
                Map.entry("operatorAccountId", evt.operatorAccountId() == null ? "" : evt.operatorAccountId().toString()),
                Map.entry("reason", evt.reason() == null ? "" : evt.reason()),
                Map.entry("reversalOf", evt.reversalOf() == null ? "" : evt.reversalOf().toString())
        );
    }
}
