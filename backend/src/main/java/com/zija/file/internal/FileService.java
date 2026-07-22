package com.zija.file.internal;

import com.zija.file.FileApi;
import com.zija.file.internal.persistence.StoredFileEntity;
import com.zija.file.internal.persistence.StoredFileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

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

    @Override
    @Transactional
    public void retain(UUID householdId, UUID fileId) {
        storedFileMapper.incrementReferenceCount(fileId, householdId);
    }

    @Override
    @Transactional
    public void release(UUID householdId, UUID fileId) {
        var entity = storedFileMapper.selectById(fileId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            return;
        }
        storedFileMapper.decrementReferenceCount(fileId, householdId);
        var updated = storedFileMapper.selectById(fileId);
        if (updated != null && updated.getReferenceCount() <= 0) {
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
