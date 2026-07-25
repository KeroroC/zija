package com.zija.inventory.internal.event;

import com.zija.inventory.StockChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 库存事件发布器（最小桩实现）。
 * <p>
 * 阶段四仅建立发布契约，同步发布事件；阶段五完成可靠投递。
 */
@Component
public class InventoryEventPublisher {

    private final ApplicationEventPublisher publisher;

    public InventoryEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(StockChangedEvent event) {
        publisher.publishEvent(event);
    }
}
