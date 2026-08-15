package com.zija.file;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 文件模块公共 API，提供附件存储、挂载查询及引用计数管理能力。
 */
public interface FileApi {

    /** 存储文件内容，自动检测媒体类型并计算 SHA-256 摘要。 */
    StoredFileInfo store(UUID householdId, byte[] content, String originalFilename, String declaredMediaType);

    /** 存储并挂到指定挂载点；撞名则拒绝。 */
    StoredFileInfo store(
            UUID householdId,
            byte[] content,
            String originalFilename,
            String declaredMediaType,
            String mountType,
            UUID mountId
    );

    /** 增加文件引用计数（文件被业务实体关联时调用）。 */
    void retain(UUID householdId, UUID fileId);

    /** 减少文件引用计数（引用计数归零时文件可被清理）。 */
    void release(UUID householdId, UUID fileId);

    /** 查询文件元信息，不存在则返回空。 */
    Optional<StoredFileInfo> findInfo(UUID householdId, UUID fileId);

    /** 分页列出当前家庭的附件。 */
    AttachmentPage list(UUID householdId, int page, int pageSize);

    /** 修改附件展示名，不改对象键。 */
    AttachmentInfo rename(UUID householdId, UUID fileId, String name);

    /** 已存储文件的元信息。 */
    record StoredFileInfo(
            UUID id,
            UUID householdId,
            String storageKey,
            String originalFilename,
            String detectedMediaType,
            long byteSize,
            String sha256
    ) {
    }

    /** 附件列表项（不含存储键）。 */
    record AttachmentInfo(
            UUID id,
            UUID householdId,
            String name,
            String mediaType,
            long byteSize,
            String mountType,
            UUID mountId,
            OffsetDateTime createdAt
    ) {
    }

    /** 附件分页结果。 */
    record AttachmentPage(
            List<AttachmentInfo> items,
            long total,
            int page,
            int pageSize
    ) {
    }
}
