package com.zija.inventory.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@TableName(value = "inventory_idempotency_record", autoResultMap = true)
public class IdempotencyRecordEntity {
    @TableId private UUID id;
    private UUID householdId;
    private String idempotencyKey;
    private String requestHash;
    private UUID movementId;
    private Map<String, Object> responsePayload;
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID v) { this.householdId = v; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String v) { this.idempotencyKey = v; }

    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String v) { this.requestHash = v; }

    public UUID getMovementId() { return movementId; }
    public void setMovementId(UUID v) { this.movementId = v; }

    public Map<String, Object> getResponsePayload() { return responsePayload; }
    public void setResponsePayload(Map<String, Object> v) { this.responsePayload = v; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
