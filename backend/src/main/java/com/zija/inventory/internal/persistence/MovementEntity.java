package com.zija.inventory.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("inventory_movement")
public class MovementEntity {
    @TableId private UUID id;
    private UUID householdId;
    private UUID lotId;
    private UUID itemId;
    private String type;
    private BigDecimal quantity;
    private UUID fromLocationId;
    private UUID toLocationId;
    private String reason;
    private String memo;
    private UUID operatorAccountId;
    private OffsetDateTime businessTime;
    private OffsetDateTime createdAt;
    private String idempotencyKey;
    private UUID reversalOf;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID v) { this.householdId = v; }

    public UUID getLotId() { return lotId; }
    public void setLotId(UUID v) { this.lotId = v; }

    public UUID getItemId() { return itemId; }
    public void setItemId(UUID v) { this.itemId = v; }

    public String getType() { return type; }
    public void setType(String v) { this.type = v; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal v) { this.quantity = v; }

    public UUID getFromLocationId() { return fromLocationId; }
    public void setFromLocationId(UUID v) { this.fromLocationId = v; }

    public UUID getToLocationId() { return toLocationId; }
    public void setToLocationId(UUID v) { this.toLocationId = v; }

    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }

    public String getMemo() { return memo; }
    public void setMemo(String v) { this.memo = v; }

    public UUID getOperatorAccountId() { return operatorAccountId; }
    public void setOperatorAccountId(UUID v) { this.operatorAccountId = v; }

    public OffsetDateTime getBusinessTime() { return businessTime; }
    public void setBusinessTime(OffsetDateTime v) { this.businessTime = v; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String v) { this.idempotencyKey = v; }

    public UUID getReversalOf() { return reversalOf; }
    public void setReversalOf(UUID v) { this.reversalOf = v; }
}
