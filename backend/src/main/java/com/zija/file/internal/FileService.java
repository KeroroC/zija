package com.zija.file.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zija.file.FileApi;
import com.zija.file.internal.exception.FileNameDuplicateException;
import com.zija.file.internal.persistence.StoredFileEntity;
import com.zija.file.internal.persistence.StoredFileMapper;
import com.zija.shared.ZijaAuditOutcome;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
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
    private final SystemApi systemApi;

    FileService(
            StoredFileMapper storedFileMapper,
            FileContentInspector inspector,
            FileStorage fileStorage,
            SystemApi systemApi
    ) {
        this.storedFileMapper = storedFileMapper;
        this.inspector = inspector;
        this.fileStorage = fileStorage;
        this.systemApi = systemApi;
    }

    /**
     * 存储文件，自动检测媒体类型并校验内容合法性，返回文件元信息。
     */
    @Override
    @Transactional
    public StoredFileInfo store(UUID householdId, byte[] content, String originalFilename, String declaredMediaType) {
        return store(householdId, content, originalFilename, declaredMediaType, null, null);
    }

    @Override
    @Transactional
    public StoredFileInfo store(
            UUID householdId,
            byte[] content,
            String originalFilename,
            String declaredMediaType,
            String mountType,
            UUID mountId
    ) {
        var inspection = inspector.inspect(content, originalFilename, declaredMediaType);
        String name = inspection.sanitizedBasename();
        String nameNormalized = normalizeName(name);
        if (mountType != null) {
            assertNameAvailable(householdId, mountType, mountId, nameNormalized, null);
        }

        String ext = switch (inspection.detectedMediaType()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            case "text/markdown" -> ".md";
            case "text/plain" -> ".txt";
            case "image/heic" -> ".heic";
            case "image/heif" -> ".heif";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> ".xlsx";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> ".pptx";
            case "application/msword" -> ".doc";
            case "application/vnd.ms-excel" -> ".xls";
            case "application/vnd.ms-powerpoint" -> ".ppt";
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
        entity.setOriginalFilename(name);
        entity.setDeclaredMediaType(declaredMediaType);
        entity.setDetectedMediaType(inspection.detectedMediaType());
        entity.setByteSize((long) content.length);
        entity.setSha256(inspection.sha256());
        entity.setReferenceCount(0);
        entity.setMountType(mountType);
        entity.setMountId(mountId);
        entity.setNameNormalized(mountType == null ? null : nameNormalized);
        storedFileMapper.insert(entity);
        if (mountType != null) {
            audit(householdId, SystemApi.AuditAction.FILE_UPLOADED, entity.getId());
        }
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

    @Override
    @Transactional(readOnly = true)
    public AttachmentPage list(UUID householdId, int page, int pageSize) {
        var query = new LambdaQueryWrapper<StoredFileEntity>()
                .eq(StoredFileEntity::getHouseholdId, householdId)
                .eq(StoredFileEntity::getMountType, "HOUSEHOLD")
                .orderByDesc(StoredFileEntity::getCreatedAt);
        var result = storedFileMapper.selectPage(new Page<>(page, pageSize), query);
        var items = result.getRecords().stream()
                .map(entity -> toAttachment(entity, householdId))
                .toList();
        return new AttachmentPage(items, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    @Transactional
    public AttachmentInfo rename(UUID householdId, UUID fileId, String name) {
        var entity = storedFileMapper.selectById(fileId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            return null;
        }
        String trimmed = name.trim();
        String nameNormalized = normalizeName(trimmed);
        assertNameAvailable(
                householdId,
                entity.getMountType(),
                entity.getMountId(),
                nameNormalized,
                fileId
        );
        entity.setOriginalFilename(trimmed);
        entity.setNameNormalized(nameNormalized);
        storedFileMapper.updateById(entity);
        audit(householdId, SystemApi.AuditAction.FILE_RENAMED, fileId);
        return toAttachment(entity, householdId);
    }

    private void audit(UUID householdId, String action, UUID fileId) {
        systemApi.recordAudit(new SystemApi.AuditEvent(
                action, ZijaAuditOutcome.SUCCESS, householdId, null, null, null, null,
                Map.of("id", fileId.toString())
        ));
    }

    private void assertNameAvailable(
            UUID householdId,
            String mountType,
            UUID mountId,
            String nameNormalized,
            UUID excludeId
    ) {
        if (mountType == null || nameNormalized.isEmpty()) {
            return;
        }
        var query = new LambdaQueryWrapper<StoredFileEntity>()
                .eq(StoredFileEntity::getHouseholdId, householdId)
                .eq(StoredFileEntity::getMountType, mountType)
                .eq(StoredFileEntity::getMountId, mountId)
                .eq(StoredFileEntity::getNameNormalized, nameNormalized);
        if (excludeId != null) {
            query.ne(StoredFileEntity::getId, excludeId);
        }
        if (storedFileMapper.selectCount(query) > 0) {
            throw new FileNameDuplicateException(nameNormalized);
        }
    }

    static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        String nfkc = Normalizer.normalize(trimmed, Normalizer.Form.NFKC);
        return nfkc.toLowerCase(Locale.ROOT);
    }

    private AttachmentInfo toAttachment(StoredFileEntity entity, UUID householdId) {
        return new AttachmentInfo(
                entity.getId(),
                entity.getHouseholdId(),
                        entity.getOriginalFilename(),
                        entity.getDetectedMediaType(),
                        entity.getByteSize(),
                        entity.getMountType() == null ? "HOUSEHOLD" : entity.getMountType(),
                        entity.getMountId() == null ? householdId : entity.getMountId(),
                        entity.getCreatedAt()
        );
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
