package com.zija.reporting.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@TableName(value = "reporting_event_dead_letter", autoResultMap = true)
public class DeadLetterEntity {
    @TableId
    private UUID id;
    private UUID eventId;
    private String eventType;
    private Map<String, Object> payload;
    private Integer failureCount;
    private OffsetDateTime nextRetryAt;
    private String lastError;
    private OffsetDateTime lastRetryAt;
    private Boolean abandoned;
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    public Integer getFailureCount() { return failureCount; }
    public void setFailureCount(Integer failureCount) { this.failureCount = failureCount; }
    public OffsetDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(OffsetDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public OffsetDateTime getLastRetryAt() { return lastRetryAt; }
    public void setLastRetryAt(OffsetDateTime lastRetryAt) { this.lastRetryAt = lastRetryAt; }
    public Boolean getAbandoned() { return abandoned; }
    public void setAbandoned(Boolean abandoned) { this.abandoned = abandoned; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
