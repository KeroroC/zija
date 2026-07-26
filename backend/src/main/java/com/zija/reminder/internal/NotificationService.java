package com.zija.reminder.internal;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zija.reminder.internal.persistence.NotificationEntity;
import com.zija.reminder.internal.persistence.NotificationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
class NotificationService {

    private final NotificationMapper notificationMapper;

    NotificationService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    @Transactional(readOnly = true)
    public NotificationPage page(UUID householdId, int pageNo, int pageSize, boolean unreadOnly) {
        var page = notificationMapper.findPage(new Page<>(pageNo, pageSize), householdId, unreadOnly);
        var items = page.getRecords().stream().map(this::toView).toList();
        return new NotificationPage(items, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID householdId) {
        return notificationMapper.countUnread(householdId);
    }

    @Transactional
    public void markOneRead(UUID householdId, UUID id) {
        notificationMapper.markOneRead(householdId, id);
    }

    @Transactional
    public void markAllRead(UUID householdId) {
        notificationMapper.markAllRead(householdId);
    }

    private NotificationView toView(NotificationEntity e) {
        return new NotificationView(e.getId(), e.getScope(), e.getTitle(), e.getMessage(),
                e.getSourceTaskId(), Boolean.TRUE.equals(e.getRead()), e.getCreatedAt());
    }

    record NotificationView(UUID id, String scope, String title, String message,
                            UUID sourceTaskId, boolean read, OffsetDateTime createdAt) {}

    record NotificationPage(List<NotificationView> items, long total, int page, int pageSize) {}
}
