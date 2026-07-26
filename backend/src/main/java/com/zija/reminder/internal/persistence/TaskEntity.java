package com.zija.reminder.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@TableName(value = "reminder_task", autoResultMap = true)
public class TaskEntity {
    @TableId private UUID id;
    private UUID householdId;
    private String kind;
    private UUID lotId;
    private UUID itemId;
    private String status;
    private OffsetDateTime dueAt;
    private String severity;
    private Map<String, Object> thresholdSnapshot;
    private BigDecimal qtySnapshot;
    private OffsetDateTime snoozedUntil;
    private OffsetDateTime lastReconciledAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version private Integer version;

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }

    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID v) { this.householdId = v; }

    public String getKind() { return kind; }
    public void setKind(String v) { this.kind = v; }

    public UUID getLotId() { return lotId; }
    public void setLotId(UUID v) { this.lotId = v; }

    public UUID getItemId() { return itemId; }
    public void setItemId(UUID v) { this.itemId = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public OffsetDateTime getDueAt() { return dueAt; }
    public void setDueAt(OffsetDateTime v) { this.dueAt = v; }

    public String getSeverity() { return severity; }
    public void setSeverity(String v) { this.severity = v; }

    public Map<String, Object> getThresholdSnapshot() { return thresholdSnapshot; }
    public void setThresholdSnapshot(Map<String, Object> v) { this.thresholdSnapshot = v; }

    public BigDecimal getQtySnapshot() { return qtySnapshot; }
    public void setQtySnapshot(BigDecimal v) { this.qtySnapshot = v; }

    public OffsetDateTime getSnoozedUntil() { return snoozedUntil; }
    public void setSnoozedUntil(OffsetDateTime v) { this.snoozedUntil = v; }

    public OffsetDateTime getLastReconciledAt() { return lastReconciledAt; }
    public void setLastReconciledAt(OffsetDateTime v) { this.lastReconciledAt = v; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer v) { this.version = v; }
}
