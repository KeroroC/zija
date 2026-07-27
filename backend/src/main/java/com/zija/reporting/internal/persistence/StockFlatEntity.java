package com.zija.reporting.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("reporting_stock_flat")
public class StockFlatEntity {
    private UUID householdId;
    private UUID lotId;
    private UUID itemId;
    private String itemName;
    private String unitName;
    private String lotNumber;
    private String serialNumber;
    private LocalDate expiryDate;
    private UUID locationId;
    private String locationPath;
    private BigDecimal quantity;
    private OffsetDateTime updatedAt;

    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID v) { this.householdId = v; }
    public UUID getLotId() { return lotId; }
    public void setLotId(UUID v) { this.lotId = v; }
    public UUID getItemId() { return itemId; }
    public void setItemId(UUID v) { this.itemId = v; }
    public String getItemName() { return itemName; }
    public void setItemName(String v) { this.itemName = v; }
    public String getUnitName() { return unitName; }
    public void setUnitName(String v) { this.unitName = v; }
    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String v) { this.lotNumber = v; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String v) { this.serialNumber = v; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate v) { this.expiryDate = v; }
    public UUID getLocationId() { return locationId; }
    public void setLocationId(UUID v) { this.locationId = v; }
    public String getLocationPath() { return locationPath; }
    public void setLocationPath(String v) { this.locationPath = v; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal v) { this.quantity = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
