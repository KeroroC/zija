package com.zija.file;

import java.util.Optional;
import java.util.UUID;

/**
 * 文件模块公共 API，提供文件存储、引用计数管理及文件信息查询能力。
 */
public interface FileApi {

    /** 存储文件内容，自动检测媒体类型并计算 SHA-256 摘要。 */
    StoredFileInfo store(UUID householdId, byte[] content, String originalFilename, String declaredMediaType);

    /** 增加文件引用计数（文件被业务实体关联时调用）。 */
    void retain(UUID householdId, UUID fileId);

    /** 减少文件引用计数（引用计数归零时文件可被清理）。 */
    void release(UUID householdId, UUID fileId);

    /** 查询文件元信息，不存在则返回空。 */
    Optional<StoredFileInfo> findInfo(UUID householdId, UUID fileId);

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
}
