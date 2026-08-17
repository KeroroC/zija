package com.zija.ai.internal.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 知识来源选择与准备状态（每家庭每附件一行）。 */
@TableName("ai_knowledge_source")
public class KnowledgeSourceEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID householdId;
    private UUID fileId;
    private String mountType;
    private UUID mountId;
    private String status;
    private String failureCode;
    private String failureMessage;
    private Integer attemptCount;
    private OffsetDateTime nextAttemptAt;
    private String disabledReason;
    private OffsetDateTime selectedAt;
    private OffsetDateTime processedAt;
    private Integer processingVersion;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }
    public UUID getFileId() { return fileId; }
    public void setFileId(UUID fileId) { this.fileId = fileId; }
    public String getMountType() { return mountType; }
    public void setMountType(String mountType) { this.mountType = mountType; }
    public UUID getMountId() { return mountId; }
    public void setMountId(UUID mountId) { this.mountId = mountId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(OffsetDateTime nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public String getDisabledReason() { return disabledReason; }
    public void setDisabledReason(String disabledReason) { this.disabledReason = disabledReason; }
    public OffsetDateTime getSelectedAt() { return selectedAt; }
    public void setSelectedAt(OffsetDateTime selectedAt) { this.selectedAt = selectedAt; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(OffsetDateTime processedAt) { this.processedAt = processedAt; }
    public Integer getProcessingVersion() { return processingVersion; }
    public void setProcessingVersion(Integer processingVersion) { this.processingVersion = processingVersion; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
