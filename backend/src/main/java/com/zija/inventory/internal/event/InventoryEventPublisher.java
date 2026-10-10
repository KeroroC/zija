package com.zija.inventory.internal.event;

import com.zija.inventory.StockChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 库存事件发布器（最小桩实现）。
 * <p>
 * 阶段四仅建立发布契约，同步发布事件；阶段五完成可靠投递。
 */
@Component
public class InventoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventPublisher.class);

    private final ApplicationEventPublisher publisher;

    public InventoryEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(StockChangedEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    logMovement(event);
                }
            });
        } else {
            logMovement(event);
        }
        publisher.publishEvent(event);
    }

    private static void logMovement(StockChangedEvent event) {
        log.info("库存流水已提交: type={} movementId={} lotId={} qty={} from={} to={}",
                event.movementType(), event.movementId(), event.lotId(), event.quantityDelta(),
                event.fromLocationId(), event.toLocationId());
    }
}
