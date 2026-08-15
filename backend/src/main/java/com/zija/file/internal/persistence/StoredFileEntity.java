package com.zija.file.internal.persistence;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("stored_file")
public class StoredFileEntity {

    @TableId
    private UUID id;
    private UUID householdId;
    private String storageKey;
    private String originalFilename;
    private String declaredMediaType;
    private String detectedMediaType;
    private Long byteSize;
    private String sha256;
    private OffsetDateTime createdAt;
    private String mountType;
    private UUID mountId;
    private String nameNormalized;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime deletedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getDeclaredMediaType() { return declaredMediaType; }
    public void setDeclaredMediaType(String declaredMediaType) { this.declaredMediaType = declaredMediaType; }
    public String getDetectedMediaType() { return detectedMediaType; }
    public void setDetectedMediaType(String detectedMediaType) { this.detectedMediaType = detectedMediaType; }
    public Long getByteSize() { return byteSize; }
    public void setByteSize(Long byteSize) { this.byteSize = byteSize; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public String getMountType() { return mountType; }
    public void setMountType(String mountType) { this.mountType = mountType; }
    public UUID getMountId() { return mountId; }
    public void setMountId(UUID mountId) { this.mountId = mountId; }
    public String getNameNormalized() { return nameNormalized; }
    public void setNameNormalized(String nameNormalized) { this.nameNormalized = nameNormalized; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}
