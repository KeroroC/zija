package com.zija.household.internal.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("household")
public class HouseholdEntity {

    @TableId(value = "singleton_key", type = IdType.INPUT)
    private Short singletonKey;

    private UUID id;

    private String name;
    private String timezone;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version
    private Integer version;

    public Short getSingletonKey() { return singletonKey; }
    public void setSingletonKey(Short singletonKey) { this.singletonKey = singletonKey; }
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
