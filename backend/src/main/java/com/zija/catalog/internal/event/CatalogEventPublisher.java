package com.zija.catalog.internal.event;

import com.zija.catalog.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * catalog 模块事件发布器。在 @Transactional 方法末尾调用，
 * Spring Modulith 在事务提交后派发给消费者。
 */
@Component
public class CatalogEventPublisher {

    private final ApplicationEventPublisher publisher;

    public CatalogEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publishItemChanged(UUID householdId, UUID itemId, String changeType) {
        publisher.publishEvent(new ItemChangedEvent(
                UUID.randomUUID(), householdId, itemId, changeType, OffsetDateTime.now()));
    }

    public void publishCategoryChanged(UUID householdId, UUID categoryId, String changeType) {
        publisher.publishEvent(new CategoryChangedEvent(
                UUID.randomUUID(), householdId, categoryId, changeType));
    }

    public void publishBrandChanged(UUID householdId, UUID brandId, String changeType) {
        publisher.publishEvent(new BrandChangedEvent(
                UUID.randomUUID(), householdId, brandId, changeType));
    }

    public void publishUnitChanged(UUID householdId, UUID unitId, String changeType) {
        publisher.publishEvent(new UnitChangedEvent(
                UUID.randomUUID(), householdId, unitId, changeType));
    }

    public void publishTagChanged(UUID householdId, UUID tagId, String changeType) {
        publisher.publishEvent(new TagChangedEvent(
                UUID.randomUUID(), householdId, tagId, changeType));
    }
}
