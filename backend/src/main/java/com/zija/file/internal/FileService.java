package com.zija.file.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zija.file.FileApi;
import com.zija.file.internal.event.FileEventPublisher;
import com.zija.file.exception.FileNameDuplicateException;
import com.zija.file.exception.FileNotAvailableException;
import com.zija.file.exception.FileNotInRecycleBinException;
import com.zija.file.internal.persistence.StoredFileEntity;
import com.zija.file.internal.persistence.StoredFileMapper;
import com.zija.shared.ZijaAuditOutcome;
import com.zija.system.SystemApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 附件管理服务，实现 {@link FileApi} 接口。
 * <p>
 * 一份附件携带挂载点（家庭 / 物品 / 批次）、可改的名字与回收站状态。
 * 删除进入回收站（{@code deletedAt} 非空），物理删除只由清除任务在保留期满后执行。
 * 同一挂载点下未删除附件名字唯一（NFKC + Locale.ROOT 折叠），回收站不占名。
 */
@Service
class FileService implements FileApi {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final StoredFileMapper storedFileMapper;
    private final FileContentInspector inspector;
    private final FileStorage fileStorage;
    private final SystemApi systemApi;
    private final FileEventPublisher eventPublisher;

    FileService(
            StoredFileMapper storedFileMapper,
            FileContentInspector inspector,
            FileStorage fileStorage,
            SystemApi systemApi,
            FileEventPublisher eventPublisher
    ) {
        this.storedFileMapper = storedFileMapper;
        this.inspector = inspector;
        this.fileStorage = fileStorage;
        this.systemApi = systemApi;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public AttachmentInfo store(
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
        assertNameAvailable(householdId, mountType, mountId, nameNormalized, null);

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
        entity.setMountType(mountType);
        entity.setMountId(mountId);
        entity.setNameNormalized(nameNormalized);
        try {
            storedFileMapper.insert(entity);
            audit(householdId, SystemApi.AuditAction.FILE_UPLOADED, entity.getId());
        } catch (RuntimeException e) {
            // 落盘已成功而 DB 未提交：删除已写的卷对象，避免崩溃/失败后遗留孤儿文件。
            // 事务回滚由 Spring 代理负责，这里只补偿跨事务的文件系统副作用。
            try {
                fileStorage.delete(storageKey);
            } catch (IOException ex) {
                log.warn("上传失败回滚卷对象失败，残留孤儿文件待完整性扫描: storageKey={}", storageKey, ex);
            }
            throw e;
        }
        return toAttachment(entity, householdId);
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
    public Optional<byte[]> readContent(UUID householdId, UUID fileId) {
        var entity = storedFileMapper.selectById(fileId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            return Optional.empty();
        }
        try {
            return Optional.of(fileStorage.read(entity.getStorageKey()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file content", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AttachmentInfo> findAttachment(UUID householdId, UUID fileId) {
        var entity = storedFileMapper.selectById(fileId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            return Optional.empty();
        }
        return Optional.of(toAttachment(entity, householdId));
    }

    @Override
    @Transactional(readOnly = true)
    public AttachmentPage list(
            UUID householdId,
            int page,
            int pageSize,
            String mountType,
            UUID mountId,
            String q,
            boolean recycled
    ) {
        var query = new LambdaQueryWrapper<StoredFileEntity>()
                .eq(StoredFileEntity::getHouseholdId, householdId);
        if (recycled) {
            query.isNotNull(StoredFileEntity::getDeletedAt);
        } else {
            query.isNull(StoredFileEntity::getDeletedAt);
        }
        if (mountType != null && !mountType.isBlank()) {
            query.eq(StoredFileEntity::getMountType, mountType);
        }
        if (mountId != null) {
            query.eq(StoredFileEntity::getMountId, mountId);
        }
        if (q != null && !q.isBlank()) {
            query.like(StoredFileEntity::getOriginalFilename, q.trim());
        }
        query.orderByDesc(StoredFileEntity::getCreatedAt);
        var result = storedFileMapper.selectPage(new Page<>(page, pageSize), query);
        var items = result.getRecords().stream()
                .map(entity -> toAttachment(entity, householdId))
                .toList();
        return new AttachmentPage(items, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachmentInfo> listByMount(UUID householdId, String mountType, UUID mountId) {
        var query = new LambdaQueryWrapper<StoredFileEntity>()
                .eq(StoredFileEntity::getHouseholdId, householdId)
                .eq(StoredFileEntity::getMountType, mountType)
                .eq(StoredFileEntity::getMountId, mountId)
                .isNull(StoredFileEntity::getDeletedAt)
                .orderByDesc(StoredFileEntity::getCreatedAt);
        return storedFileMapper.selectList(query).stream()
                .map(entity -> toAttachment(entity, householdId))
                .toList();
    }

    @Override
    @Transactional
    public AttachmentInfo rename(UUID householdId, UUID fileId, String name) {
        var entity = requireEntity(householdId, fileId);
        if (entity == null) {
            return null;
        }
        // 与上传同一套清洗：去路径分隔符、控制符与双引号，避免脏名进入 Content-Disposition
        String sanitized = FileContentInspector.sanitizeName(name);
        if (sanitized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件名包含非法字符");
        }
        String nameNormalized = normalizeName(sanitized);
        assertNameAvailable(
                householdId,
                entity.getMountType(),
                entity.getMountId(),
                nameNormalized,
                fileId
        );
        entity.setOriginalFilename(sanitized);
        entity.setNameNormalized(nameNormalized);
        storedFileMapper.updateById(entity);
        audit(householdId, SystemApi.AuditAction.FILE_RENAMED, fileId);
        return toAttachment(entity, householdId);
    }

    @Override
    @Transactional
    public AttachmentInfo recycle(UUID householdId, UUID fileId) {
        return doRecycle(householdId, fileId, true);
    }

    @Override
    @Transactional
    public AttachmentInfo recycleSilently(UUID householdId, UUID fileId) {
        return doRecycle(householdId, fileId, false);
    }

    private AttachmentInfo doRecycle(UUID householdId, UUID fileId, boolean publishEvent) {
        var entity = requireEntity(householdId, fileId);
        if (entity == null) {
            return null;
        }
        if (entity.getDeletedAt() != null) {
            // 幂等：已在回收站
            return toAttachment(entity, householdId);
        }
        entity.setDeletedAt(OffsetDateTime.now());
        storedFileMapper.updateById(entity);
        if (publishEvent) {
            eventPublisher.publishRecycled(
                    householdId, fileId, entity.getMountType(), entity.getMountId());
        }
        audit(householdId, SystemApi.AuditAction.FILE_DELETED, fileId);
        return toAttachment(entity, householdId);
    }

    @Override
    @Transactional
    public AttachmentInfo restore(UUID householdId, UUID fileId) {
        var entity = requireEntity(householdId, fileId);
        if (entity == null) {
            return null;
        }
        if (entity.getDeletedAt() == null) {
            return toAttachment(entity, householdId);
        }
        // 恢复回到删除前的挂载点；目标挂载点已有同名未删除附件则必须改名（撞名拒绝）
        assertNameAvailable(
                householdId,
                entity.getMountType(),
                entity.getMountId(),
                entity.getNameNormalized(),
                fileId
        );
        int restored = storedFileMapper.restoreIfRecycled(householdId, fileId);
        if (restored == 0) {
            // 永久删除或另一场恢复已先取得行级竞争：重新读取，绝不返回旧实体。
            var current = requireEntity(householdId, fileId);
            if (current == null) {
                return null;
            }
            if (current.getDeletedAt() == null) {
                return toAttachment(current, householdId);
            }
            throw new FileNotAvailableException(fileId);
        }
        entity.setDeletedAt(null);
        eventPublisher.publishRestored(
                householdId, fileId, entity.getMountType(), entity.getMountId());
        audit(householdId, SystemApi.AuditAction.FILE_RESTORED, fileId);
        return toAttachment(entity, householdId);
    }

    @Override
    @Transactional
    public AttachmentInfo remount(UUID householdId, UUID fileId, String mountType, UUID mountId) {
        var entity = requireEntity(householdId, fileId);
        if (entity == null) {
            return null;
        }
        if (entity.getDeletedAt() != null) {
            throw new FileNotAvailableException(fileId);
        }
        if (mountType.equals(entity.getMountType()) && mountId.equals(entity.getMountId())) {
            return toAttachment(entity, householdId);
        }
        String nameNormalized = entity.getNameNormalized();
        assertNameAvailable(householdId, mountType, mountId, nameNormalized, fileId);
        String oldMountType = entity.getMountType();
        UUID oldMountId = entity.getMountId();
        entity.setMountType(mountType);
        entity.setMountId(mountId);
        storedFileMapper.updateById(entity);
        eventPublisher.publishMoved(
                householdId, fileId, oldMountType, oldMountId, mountType, mountId);
        audit(householdId, SystemApi.AuditAction.FILE_MOVED, fileId);
        return toAttachment(entity, householdId);
    }

    @Override
    @Transactional
    public boolean purge(UUID householdId, UUID fileId) {
        var entity = requireEntity(householdId, fileId);
        if (entity == null) {
            return false;
        }
        if (entity.getDeletedAt() == null) {
            throw new FileNotInRecycleBinException(fileId);
        }
        // 先以行级条件删除「认领」该行并判定并发：与「恢复」竞争时，恢复先赢则受影响行数为 0 → 冲突，
        // 且不再触碰卷对象，避免已恢复的活附件内容被删造成悬空。
        int rows = storedFileMapper.delete(new LambdaQueryWrapper<StoredFileEntity>()
                .eq(StoredFileEntity::getId, fileId)
                .eq(StoredFileEntity::getHouseholdId, householdId)
                .isNotNull(StoredFileEntity::getDeletedAt));
        if (rows == 0) {
            throw new FileNotInRecycleBinException(fileId);
        }
        audit(householdId, SystemApi.AuditAction.FILE_PURGED, fileId);
        eventPublisher.publishPurged(householdId, fileId);
        try {
            fileStorage.delete(entity.getStorageKey());
        } catch (IOException e) {
            // 卷上对象删除失败：抛异常使本事务回滚（认领删除与审计一并撤销），行留在回收站交定时任务重试
            log.warn("永久删除附件卷对象失败，保留记录待重试: fileId={} storageKey={}",
                    entity.getId(), entity.getStorageKey(), e);
            throw new RuntimeException("Failed to purge file storage", e);
        }
        return true;
    }

    @Override
    @Transactional
    public int purgeExpired(OffsetDateTime before) {
        var expired = storedFileMapper.findExpired(before);
        int purged = 0;
        for (var entity : expired) {
            // 与 purge() 同一顺序：先以行级条件删除「认领」该行，再删卷对象。
            // 崩溃窗口只留「无引用 + 文件仍在」的孤儿文件（可由完整性报告检出），
            // 不留「引用仍在 + 文件已删」的悬空行；并发恢复先赢时受影响行数为 0 → 跳过，不触碰卷对象。
            int rows = storedFileMapper.delete(new LambdaQueryWrapper<StoredFileEntity>()
                    .eq(StoredFileEntity::getId, entity.getId())
                    .eq(StoredFileEntity::getHouseholdId, entity.getHouseholdId())
                    .isNotNull(StoredFileEntity::getDeletedAt));
            if (rows == 0) {
                // 已被恢复或并发清除：不删卷对象，避免删掉已恢复活附件的内容
                continue;
            }
            try {
                fileStorage.delete(entity.getStorageKey());
            } catch (IOException e) {
                // 行已认领删除但卷上对象删除失败：回插该行保留待重试，避免留下无引用的孤儿文件
                storedFileMapper.insert(entity);
                log.warn("物理删除附件卷对象失败，回插记录待重试: fileId={} storageKey={}",
                        entity.getId(), entity.getStorageKey(), e);
                continue;
            }
            eventPublisher.publishPurged(entity.getHouseholdId(), entity.getId());
            purged++;
        }
        return purged;
    }

    private StoredFileEntity requireEntity(UUID householdId, UUID fileId) {
        var entity = storedFileMapper.selectById(fileId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            return null;
        }
        return entity;
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
        if (mountType == null || nameNormalized == null || nameNormalized.isEmpty()) {
            return;
        }
        var query = new LambdaQueryWrapper<StoredFileEntity>()
                .eq(StoredFileEntity::getHouseholdId, householdId)
                .eq(StoredFileEntity::getMountType, mountType)
                .eq(StoredFileEntity::getMountId, mountId)
                .eq(StoredFileEntity::getNameNormalized, nameNormalized)
                .isNull(StoredFileEntity::getDeletedAt);
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
                entity.getMountType(),
                entity.getMountId(),
                entity.getCreatedAt(),
                entity.getDeletedAt()
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
