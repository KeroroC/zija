package com.zija.reminder.internal.mail;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@TableName(value = "reminder_household_mail_setting", autoResultMap = true)
public class MailSettingEntity {
    @TableId private UUID id;
    private UUID householdId;
    private Boolean digestEnabled;
    private String digestFrequency;
    private Boolean urgentEnabled;
    @TableField(typeHandler = StringArrayTypeHandler.class)
    private List<String> recipientRoles;
    private OffsetDateTime lastDigestSentAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version private Integer version;

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }

    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID v) { this.householdId = v; }

    public Boolean getDigestEnabled() { return digestEnabled; }
    public void setDigestEnabled(Boolean v) { this.digestEnabled = v; }

    public String getDigestFrequency() { return digestFrequency; }
    public void setDigestFrequency(String v) { this.digestFrequency = v; }

    public Boolean getUrgentEnabled() { return urgentEnabled; }
    public void setUrgentEnabled(Boolean v) { this.urgentEnabled = v; }

    public List<String> getRecipientRoles() { return recipientRoles; }
    public void setRecipientRoles(List<String> v) { this.recipientRoles = v; }

    public OffsetDateTime getLastDigestSentAt() { return lastDigestSentAt; }
    public void setLastDigestSentAt(OffsetDateTime v) { this.lastDigestSentAt = v; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer v) { this.version = v; }
}
