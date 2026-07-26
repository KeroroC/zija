package com.zija.reminder.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("reminder_notification")
public class NotificationEntity {
    @TableId private UUID id;
    private UUID householdId;
    private String scope;
    private String title;
    private String message;
    private UUID sourceTaskId;
    private Boolean read;
    private OffsetDateTime createdAt;
    private OffsetDateTime readAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }

    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID v) { this.householdId = v; }

    public String getScope() { return scope; }
    public void setScope(String v) { this.scope = v; }

    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }

    public String getMessage() { return message; }
    public void setMessage(String v) { this.message = v; }

    public UUID getSourceTaskId() { return sourceTaskId; }
    public void setSourceTaskId(UUID v) { this.sourceTaskId = v; }

    public Boolean getRead() { return read; }
    public void setRead(Boolean v) { this.read = v; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }

    public OffsetDateTime getReadAt() { return readAt; }
    public void setReadAt(OffsetDateTime v) { this.readAt = v; }
}
