package com.zija.reminder.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("reminder_processed_event")
public class ProcessedEventEntity {
    @TableId private UUID eventId;
    private OffsetDateTime processedAt;

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID v) { this.eventId = v; }

    public OffsetDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(OffsetDateTime v) { this.processedAt = v; }
}
