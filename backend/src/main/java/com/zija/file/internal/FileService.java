package com.zija.file.internal;

import com.zija.file.FileApi;
import com.zija.file.internal.persistence.StoredFileEntity;
import com.zija.file.internal.persistence.StoredFileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * 文件管理服务，实现 {@link FileApi} 接口。
 * <p>
 * 负责文件的存储、引用计数和生命周期管理。存储前通过 {@link FileContentInspector} 校验文件内容，
 * 确保媒体类型合法且内容与声明一致。采用引用计数机制，当引用归零时自动清理物理文件和数据库记录，
 * 避免存储空间泄漏。
 */
@Service
class FileService implements FileApi {

    private final StoredFileMapper storedFileMapper;
    private final FileContentInspector inspector;
    private final FileStorage fileStorage;

    FileService(
            StoredFileMapper storedFileMapper,
            FileContentInspector inspector,
            FileStorage fileStorage
    ) {
        this.storedFileMapper = storedFileMapper;
        this.inspector = inspector;
        this.fileStorage = fileStorage;
    }

    /**
     * 存储文件，自动检测媒体类型并校验内容合法性，返回文件元信息。
     */
    @Override
    @Transactional
    public StoredFileInfo store(UUID householdId, byte[] content, String originalFilename, String declaredMediaType) {
        var inspection = inspector.inspect(content, originalFilename, declaredMediaType);

        String ext = switch (inspection.detectedMediaType()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };

        String storageKey;
        try {
            storageKey = fileStorage.store(content, ext);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }

        var entity = new StoredFileEntity();
        entity.setId(UUID.randomUUID());
        entity.setHouseholdId(householdId);
        entity.setStorageKey(storageKey);
        entity.setOriginalFilename(inspection.sanitizedBasename());
        entity.setDeclaredMediaType(declaredMediaType);
        entity.setDetectedMediaType(inspection.detectedMediaType());
        entity.setByteSize((long) content.length);
        entity.setSha256(inspection.sha256());
        entity.setReferenceCount(0);
        storedFileMapper.insert(entity);

        return toInfo(entity);
    }

    /**
     * 增加文件引用计数，表示有新的业务实体引用该文件。
     */
    @Override
    @Transactional
    public void retain(UUID householdId, UUID fileId) {
        storedFileMapper.incrementReferenceCount(fileId, householdId);
    }

    /**
     * 释放文件引用，引用计数归零时自动删除物理文件和数据库记录。
     * <p>
     * 使用原子 SQL 操作 {@code UPDATE ... SET reference_count = reference_count - 1 WHERE reference_count > 0 RETURNING}
     * 将递减和条件检查合并为单次数据库调用，消除并发竞态条件。
     */
    @Override
    @Transactional
    public void release(UUID householdId, UUID fileId) {
        var updated = storedFileMapper.decrementReferenceCountIfPositive(fileId, householdId);
        if (updated == null) {
            return; // 引用计数已为 0 或文件不存在/不属于该家庭
        }
        if (updated.getReferenceCount() == 0) {
            try {
                fileStorage.delete(updated.getStorageKey());
            } catch (IOException e) {
                // Log but don't fail — orphaned file can be cleaned up later
            }
            storedFileMapper.deleteById(fileId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredFileInfo> findInfo(UUID householdId, UUID fileId) {
        var entity = storedFileMapper.selectById(fileId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            return Optional.empty();
        }
        return Optional.of(toInfo(entity));
    }

    private StoredFileInfo toInfo(StoredFileEntity entity) {
        return new StoredFileInfo(
                entity.getId(),
                entity.getHouseholdId(),
                entity.getStorageKey(),
                entity.getOriginalFilename(),
                entity.getDetectedMediaType(),
                entity.getByteSize(),
                entity.getSha256()
        );
    }
}
