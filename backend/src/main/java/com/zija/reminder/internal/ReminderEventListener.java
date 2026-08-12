package com.zija.reminder.internal;

import com.zija.inventory.StockChangedEvent;
import com.zija.reminder.internal.persistence.DeadLetterEntity;
import com.zija.reminder.internal.persistence.DeadLetterMapper;
import com.zija.reminder.internal.persistence.ProcessedEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
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

    /**
     * 使用 {@link TransactionalEventListener} + AFTER_COMMIT 阶段，保证父事务（库存命令）
     * 提交后再读取库存聚合。{@link ApplicationEventPublisher#publishEvent} 在事务内同步
     * 派发到 {@code @EventListener}，会让本监听器在父事务尚未提交时启动 REQUIRES_NEW 事务，
     * 读不到刚写入的库存位（qty=0），从而误判低库存。阶段六迁移到 {@code spring-modulith-starter-jdbc}
     * 后，发布侧仍是同步 ApplicationEventPublisher，所以必须显式 AFTER_COMMIT 才能让
     * reconciler 看到正确的提交后库存。
     *
     * 失败时写 dead-letter（独立事务提交），不向上抛异常。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockChanged(StockChangedEvent evt) {
        try {
            processStockChangedEvent(evt);
        } catch (RuntimeException ex) {
            // 用独立事务写 dead-letter，确保主事务回滚后仍保留
            saveDeadLetterInNewTx(evt, ex);
            log.warn("StockChangedEvent reconcile failed, wrote dead-letter: eventId={}", evt.eventId(), ex);
        }
    }

    /**
     * 实际的库存变更处理：插入去重行 + 调用 reconciler。
     * 失败时向上抛异常，由调用方决定写 dead-letter 或走重投逻辑。
     * 供 {@link EventRetryService} 重投时调用——必须用此方法而非 {@link #onStockChanged}，
     * 否则监听器的 catch 会吞掉异常，重投服务无法触发 incrementFailure / markAbandoned。
     *
     * 失败时清理 dedup 行：dedup insert 在本方法内、不在 REQUIRES_NEW 内提交，
     * 若不在失败时清理，下次重试 insertOnConflictDoNothing 会返回 0 跳过工作，
     * 重投服务误判为成功而 deleteById 死信，导致事件丢失。
     */
    public void processStockChangedEvent(StockChangedEvent evt) {
        int rows = processedEventMapper.insertOnConflictDoNothing(evt.eventId());
        if (rows == 0) return; // 已处理，跳过
        try {
            // 在独立事务中调用 reconciler，避免失败标记外层事务为 rollback-only
            reconcilerInNewTx(evt);
        } catch (RuntimeException ex) {
            // 清理 dedup 行，允许下次重试重新处理
            processedEventMapper.deleteById(evt.eventId());
            throw ex;
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
