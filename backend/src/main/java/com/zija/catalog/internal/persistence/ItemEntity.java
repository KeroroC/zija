package com.zija.catalog.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@TableName("catalog_item")
public class ItemEntity {
    @TableId private UUID id;
    private UUID householdId;
    private String name;
    private String managementType;
    private UUID categoryId;
    private UUID brandId;
    private UUID unitId;
    private UUID coverFileId;
    private String memo;
    private String expiryReminderMode;
    private List<Short> expiryReminderDays;
    private String lowStockMode;
    private BigDecimal lowStockThreshold;
    private String status;
    private OffsetDateTime archivedAt;
    private UUID archivedBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version private Integer version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getManagementType() { return managementType; }
    public void setManagementType(String managementType) { this.managementType = managementType; }
    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }
    public UUID getBrandId() { return brandId; }
    public void setBrandId(UUID brandId) { this.brandId = brandId; }
    public UUID getUnitId() { return unitId; }
    public void setUnitId(UUID unitId) { this.unitId = unitId; }
    public UUID getCoverFileId() { return coverFileId; }
    public void setCoverFileId(UUID coverFileId) { this.coverFileId = coverFileId; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public String getExpiryReminderMode() { return expiryReminderMode; }
    public void setExpiryReminderMode(String expiryReminderMode) { this.expiryReminderMode = expiryReminderMode; }
    public List<Short> getExpiryReminderDays() { return expiryReminderDays; }
    public void setExpiryReminderDays(List<Short> expiryReminderDays) { this.expiryReminderDays = expiryReminderDays; }
    public String getLowStockMode() { return lowStockMode; }
    public void setLowStockMode(String lowStockMode) { this.lowStockMode = lowStockMode; }
    public BigDecimal getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(BigDecimal lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(OffsetDateTime archivedAt) { this.archivedAt = archivedAt; }
    public UUID getArchivedBy() { return archivedBy; }
    public void setArchivedBy(UUID archivedBy) { this.archivedBy = archivedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
