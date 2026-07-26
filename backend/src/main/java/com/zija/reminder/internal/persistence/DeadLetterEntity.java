package com.zija.reminder.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@TableName(value = "reminder_event_dead_letter", autoResultMap = true)
public class DeadLetterEntity {
    @TableId private UUID id;
    private UUID eventId;
    private Map<String, Object> payload;
    private Integer failureCount;
    private OffsetDateTime nextRetryAt;
    private String lastError;
    private OffsetDateTime lastRetryAt;
    private Boolean abandoned;
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID v) { this.eventId = v; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> v) { this.payload = v; }

    public Integer getFailureCount() { return failureCount; }
    public void setFailureCount(Integer v) { this.failureCount = v; }

    public OffsetDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(OffsetDateTime v) { this.nextRetryAt = v; }

    public String getLastError() { return lastError; }
    public void setLastError(String v) { this.lastError = v; }

    public OffsetDateTime getLastRetryAt() { return lastRetryAt; }
    public void setLastRetryAt(OffsetDateTime v) { this.lastRetryAt = v; }

    public Boolean getAbandoned() { return abandoned; }
    public void setAbandoned(Boolean v) { this.abandoned = v; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
