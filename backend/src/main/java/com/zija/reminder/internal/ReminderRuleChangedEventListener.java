package com.zija.reminder.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 监听规则变更事件，在规则写入事务提交后对家庭全量重算提醒任务。
 * <p>
 * 使用 AFTER_COMMIT + REQUIRES_NEW：规则写入成功提交后再触发重算，
 * 重算失败只记日志、不破坏已提交的规则（重算失败由每日扫描兜底）。
 * </p>
 */
@Service
class ReminderRuleChangedEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReminderRuleChangedEventListener.class);

    private final ExpiryScanScheduler expiryScanScheduler;
    private final TransactionTemplate requiresNewTx;

    ReminderRuleChangedEventListener(ExpiryScanScheduler expiryScanScheduler,
                                     PlatformTransactionManager txManager) {
        this.expiryScanScheduler = expiryScanScheduler;
        this.requiresNewTx = new TransactionTemplate(txManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRuleChanged(ReminderRuleChangedEvent evt) {
        try {
            requiresNewTx.executeWithoutResult(status ->
                    expiryScanScheduler.reconcileAllForHousehold(evt.householdId()));
        } catch (RuntimeException ex) {
            // 仅记日志，不破坏已提交的规则；重算失败由每日 03:00 全量扫描兜底
            log.warn("Rule-changed reconcile failed for household {}: {}",
                    evt.householdId(), ex.getMessage());
        }
    }
}
