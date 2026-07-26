package com.zija.reminder.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@TableName(value = "reminder_household_rule", autoResultMap = true)
public class HouseholdRuleEntity {
    @TableId private UUID id;
    private UUID householdId;
    private Boolean expiryDisabled;
    @TableField(typeHandler = ShortArrayTypeHandler.class)
    private List<Short> expiryReminderDays;
    private Boolean lowStockDisabled;
    private BigDecimal lowStockThreshold;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version private Integer version;

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }

    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID v) { this.householdId = v; }

    public Boolean getExpiryDisabled() { return expiryDisabled; }
    public void setExpiryDisabled(Boolean v) { this.expiryDisabled = v; }

    public List<Short> getExpiryReminderDays() { return expiryReminderDays; }
    public void setExpiryReminderDays(List<Short> v) { this.expiryReminderDays = v; }

    public Boolean getLowStockDisabled() { return lowStockDisabled; }
    public void setLowStockDisabled(Boolean v) { this.lowStockDisabled = v; }

    public BigDecimal getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(BigDecimal v) { this.lowStockThreshold = v; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer v) { this.version = v; }
}
