package com.zija.inventory.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("inventory_lot")
public class LotEntity {
    @TableId private UUID id;
    private UUID householdId;
    private UUID itemId;
    private LocalDate purchaseDate;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private String lotNumber;
    private String serialNumber;
    private String memo;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version private Integer version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID v) { this.householdId = v; }

    public UUID getItemId() { return itemId; }
    public void setItemId(UUID v) { this.itemId = v; }

    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate v) { this.purchaseDate = v; }

    public LocalDate getProductionDate() { return productionDate; }
    public void setProductionDate(LocalDate v) { this.productionDate = v; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate v) { this.expiryDate = v; }

    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String v) { this.lotNumber = v; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String v) { this.serialNumber = v; }

    public String getMemo() { return memo; }
    public void setMemo(String v) { this.memo = v; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer v) { this.version = v; }
}
