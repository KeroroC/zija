package com.zija.household.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("invitation")
public class InvitationEntity {

    @TableId
    private UUID id;
    private UUID householdId;
    private String tokenDigest;
    private String role;
    private OffsetDateTime expiresAt;
    private UUID createdBy;
    private OffsetDateTime consumedAt;
    private UUID consumedBy;
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }
    public String getTokenDigest() { return tokenDigest; }
    public void setTokenDigest(String tokenDigest) { this.tokenDigest = tokenDigest; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getConsumedAt() { return consumedAt; }
    public void setConsumedAt(OffsetDateTime consumedAt) { this.consumedAt = consumedAt; }
    public UUID getConsumedBy() { return consumedBy; }
    public void setConsumedBy(UUID consumedBy) { this.consumedBy = consumedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
