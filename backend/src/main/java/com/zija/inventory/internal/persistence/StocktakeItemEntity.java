package com.zija.inventory.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.util.UUID;

@TableName("inventory_stocktake_item")
public class StocktakeItemEntity {
    @TableId private UUID id;
    private UUID stocktakeId;
    private UUID householdId;
    private UUID lotId;
    private UUID locationId;
    private BigDecimal bookQuantity;
    private BigDecimal actualQuantity;
    private Long positionRevision;
    private String reason;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getStocktakeId() { return stocktakeId; }
    public void setStocktakeId(UUID v) { this.stocktakeId = v; }

    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID v) { this.householdId = v; }

    public UUID getLotId() { return lotId; }
    public void setLotId(UUID v) { this.lotId = v; }

    public UUID getLocationId() { return locationId; }
    public void setLocationId(UUID v) { this.locationId = v; }

    public BigDecimal getBookQuantity() { return bookQuantity; }
    public void setBookQuantity(BigDecimal v) { this.bookQuantity = v; }

    public BigDecimal getActualQuantity() { return actualQuantity; }
    public void setActualQuantity(BigDecimal v) { this.actualQuantity = v; }

    public Long getPositionRevision() { return positionRevision; }
    public void setPositionRevision(Long v) { this.positionRevision = v; }

    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
}
