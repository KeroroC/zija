package com.zija.system.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@TableName("audit_log")
public class AuditLogEntity {

    @TableId
    private UUID id;
    private UUID householdId;
    private UUID actorAccountId;
    private UUID subjectAccountId;
    private String action;
    private String outcome;
    private Map<String, Object> detail;
    private String ipAddress;
    private String requestId;
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }
    public UUID getActorAccountId() { return actorAccountId; }
    public void setActorAccountId(UUID actorAccountId) { this.actorAccountId = actorAccountId; }
    public UUID getSubjectAccountId() { return subjectAccountId; }
    public void setSubjectAccountId(UUID subjectAccountId) { this.subjectAccountId = subjectAccountId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public Map<String, Object> getDetail() { return detail; }
    public void setDetail(Map<String, Object> detail) { this.detail = detail; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
