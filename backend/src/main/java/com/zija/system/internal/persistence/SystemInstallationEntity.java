package com.zija.system.internal.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;
import java.util.UUID;

@TableName(value = "system_installation", autoResultMap = true)
public class SystemInstallationEntity {

    @TableId(value = "singleton_key", type = IdType.INPUT)
    private Short singletonKey;

    @TableField(typeHandler = PostgresUuidTypeHandler.class)
    private UUID installationId;

    private OffsetDateTime createdAt;

    public Short getSingletonKey() {
        return singletonKey;
    }

    public void setSingletonKey(Short singletonKey) {
        this.singletonKey = singletonKey;
    }

    public UUID getInstallationId() {
        return installationId;
    }

    public void setInstallationId(UUID installationId) {
        this.installationId = installationId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
