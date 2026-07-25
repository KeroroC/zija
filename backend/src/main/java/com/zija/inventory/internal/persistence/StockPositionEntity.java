package com.zija.inventory.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("inventory_stock_position")
public class StockPositionEntity {
    @TableId private UUID id;
    private UUID householdId;
    private UUID lotId;
    private UUID locationId;
    private BigDecimal quantity;
    private Long revision;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID v) { this.householdId = v; }

    public UUID getLotId() { return lotId; }
    public void setLotId(UUID v) { this.lotId = v; }

    public UUID getLocationId() { return locationId; }
    public void setLocationId(UUID v) { this.locationId = v; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal v) { this.quantity = v; }

    public Long getRevision() { return revision; }
    public void setRevision(Long v) { this.revision = v; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
