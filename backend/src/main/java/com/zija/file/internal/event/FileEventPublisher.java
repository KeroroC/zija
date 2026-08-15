package com.zija.file.internal.event;

import com.zija.file.AttachmentMovedEvent;
import com.zija.file.AttachmentRecycledEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * file 模块事件发布器。在 {@code @Transactional} 方法末尾调用；
 * 普通 {@code @EventListener} 消费者会在同一事务内同步收到事件。
 */
@Component
public class FileEventPublisher {

    private final ApplicationEventPublisher publisher;

    public FileEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publishRecycled(UUID householdId, UUID fileId, String mountType, UUID mountId) {
        publisher.publishEvent(new AttachmentRecycledEvent(
                UUID.randomUUID(), householdId, fileId, mountType, mountId, OffsetDateTime.now()));
    }

    public void publishMoved(
            UUID householdId, UUID fileId,
            String oldMountType, UUID oldMountId,
            String newMountType, UUID newMountId
    ) {
        publisher.publishEvent(new AttachmentMovedEvent(
                UUID.randomUUID(), householdId, fileId,
                oldMountType, oldMountId, newMountType, newMountId,
                OffsetDateTime.now()));
    }
}
