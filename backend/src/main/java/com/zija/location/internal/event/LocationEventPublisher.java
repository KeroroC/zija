package com.zija.location.internal.event;

import com.zija.location.LocationChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * location 模块事件发布器。在 @Transactional 方法末尾调用。
 */
@Component
public class LocationEventPublisher {

    private final ApplicationEventPublisher publisher;

    public LocationEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publishLocationChanged(UUID householdId, UUID locationId,
                                        String changeType, UUID parentId) {
        publisher.publishEvent(new LocationChangedEvent(
                UUID.randomUUID(), householdId, locationId, changeType,
                parentId, OffsetDateTime.now()));
    }
}
