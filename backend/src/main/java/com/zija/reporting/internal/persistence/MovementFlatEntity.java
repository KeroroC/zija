package com.zija.reporting.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("reporting_movement_flat")
public class MovementFlatEntity {
    private UUID householdId;
    @TableId
    private UUID movementId;
    private UUID eventId;
    private UUID lotId;
    private UUID itemId;
    private String itemName;
    private String type;
    private BigDecimal quantityDelta;
    private UUID fromLocationId;
    private UUID toLocationId;
    private String fromLocationPath;
    private String toLocationPath;
    private UUID operatorAccountId;
    private String operatorDisplayName;
    private String reason;
    private UUID reversalOf;
    private OffsetDateTime businessTime;
    private OffsetDateTime createdAt;

    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID v) { this.householdId = v; }
    public UUID getMovementId() { return movementId; }
    public void setMovementId(UUID v) { this.movementId = v; }
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID v) { this.eventId = v; }
    public UUID getLotId() { return lotId; }
    public void setLotId(UUID v) { this.lotId = v; }
    public UUID getItemId() { return itemId; }
    public void setItemId(UUID v) { this.itemId = v; }
    public String getItemName() { return itemName; }
    public void setItemName(String v) { this.itemName = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public BigDecimal getQuantityDelta() { return quantityDelta; }
    public void setQuantityDelta(BigDecimal v) { this.quantityDelta = v; }
    public UUID getFromLocationId() { return fromLocationId; }
    public void setFromLocationId(UUID v) { this.fromLocationId = v; }
    public UUID getToLocationId() { return toLocationId; }
    public void setToLocationId(UUID v) { this.toLocationId = v; }
    public String getFromLocationPath() { return fromLocationPath; }
    public void setFromLocationPath(String v) { this.fromLocationPath = v; }
    public String getToLocationPath() { return toLocationPath; }
    public void setToLocationPath(String v) { this.toLocationPath = v; }
    public UUID getOperatorAccountId() { return operatorAccountId; }
    public void setOperatorAccountId(UUID v) { this.operatorAccountId = v; }
    public String getOperatorDisplayName() { return operatorDisplayName; }
    public void setOperatorDisplayName(String v) { this.operatorDisplayName = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public UUID getReversalOf() { return reversalOf; }
    public void setReversalOf(UUID v) { this.reversalOf = v; }
    public OffsetDateTime getBusinessTime() { return businessTime; }
    public void setBusinessTime(OffsetDateTime v) { this.businessTime = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
