package com.zija.file;

import java.util.Optional;
import java.util.UUID;

public interface FileApi {

    StoredFileInfo store(UUID householdId, byte[] content, String originalFilename, String declaredMediaType);

    void retain(UUID householdId, UUID fileId);

    void release(UUID householdId, UUID fileId);

    Optional<StoredFileInfo> findInfo(UUID householdId, UUID fileId);

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
