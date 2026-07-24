package com.zija.file.internal;

import com.zija.file.internal.persistence.StoredFileEntity;
import com.zija.file.internal.persistence.StoredFileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FileServiceTest {

    private StoredFileMapper storedFileMapper;
    private FileContentInspector inspector;
    private FileStorage fileStorage;
    private FileService service;

    private final UUID householdId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        storedFileMapper = mock(StoredFileMapper.class);
        inspector = mock(FileContentInspector.class);
        fileStorage = mock(FileStorage.class);
        service = new FileService(storedFileMapper, inspector, fileStorage);
    }

    @Test
    void storeCallsInspectorThenFileStorageThenMapperInsert() throws IOException {
        byte[] content = new byte[]{1, 2, 3};
        var inspectionResult = new FileContentInspector.InspectionResult(
                "image/jpeg", "photo.jpg", "abc123hash");
        when(inspector.inspect(content, "photo.jpg", "image/jpeg")).thenReturn(inspectionResult);
        when(fileStorage.store(content, ".jpg")).thenReturn("2026/07/uuid.jpg");

        var result = service.store(householdId, content, "photo.jpg", "image/jpeg");

        var order = inOrder(inspector, fileStorage, storedFileMapper);
        order.verify(inspector).inspect(content, "photo.jpg", "image/jpeg");
        order.verify(fileStorage).store(content, ".jpg");
        order.verify(storedFileMapper).insert(any(StoredFileEntity.class));

        assertThat(result.householdId()).isEqualTo(householdId);
        assertThat(result.storageKey()).isEqualTo("2026/07/uuid.jpg");
        assertThat(result.originalFilename()).isEqualTo("photo.jpg");
        assertThat(result.detectedMediaType()).isEqualTo("image/jpeg");
        assertThat(result.sha256()).isEqualTo("abc123hash");
    }

    @Test
    void retainCallsMapperIncrementReferenceCount() {
        UUID fileId = UUID.randomUUID();
        service.retain(householdId, fileId);
        verify(storedFileMapper).incrementReferenceCount(fileId, householdId);
    }

    @Test
    void releaseDeletesFileAndEntityWhenReferenceCountReachesZero() throws IOException {
        UUID fileId = UUID.randomUUID();
        var decrementedEntity = updatedEntity(fileId, householdId, "2026/07/uuid.jpg", 0);

        when(storedFileMapper.decrementReferenceCountIfPositive(fileId, householdId))
                .thenReturn(decrementedEntity);

        service.release(householdId, fileId);

        verify(storedFileMapper).decrementReferenceCountIfPositive(fileId, householdId);
        verify(fileStorage).delete("2026/07/uuid.jpg");
        verify(storedFileMapper).deleteById(fileId);
    }

    @Test
    void releaseDoesNotDeleteWhenReferencesRemain() throws IOException {
        UUID fileId = UUID.randomUUID();
        var decrementedEntity = updatedEntity(fileId, householdId, "2026/07/uuid.jpg", 1);

        when(storedFileMapper.decrementReferenceCountIfPositive(fileId, householdId))
                .thenReturn(decrementedEntity);

        service.release(householdId, fileId);

        verify(storedFileMapper).decrementReferenceCountIfPositive(fileId, householdId);
        verify(fileStorage, never()).delete(anyString());
        verify(storedFileMapper, never()).deleteById(any(UUID.class));
    }

    @Test
    void releaseDoesNothingWhenReferenceCountAlreadyZero() throws IOException {
        UUID fileId = UUID.randomUUID();

        when(storedFileMapper.decrementReferenceCountIfPositive(fileId, householdId))
                .thenReturn(null); // 引用计数已为 0 或文件不存在

        service.release(householdId, fileId);

        verify(storedFileMapper).decrementReferenceCountIfPositive(fileId, householdId);
        verify(fileStorage, never()).delete(anyString());
        verify(storedFileMapper, never()).deleteById(any(UUID.class));
    }

    @Test
    void storeRejectsOversizedContent() {
        byte[] content = new byte[5 * 1024 * 1024 + 1];
        when(inspector.inspect(content, "big.jpg", "image/jpeg"))
                .thenThrow(new FileTooLargeException(content.length));

        assertThatThrownBy(() -> service.store(householdId, content, "big.jpg", "image/jpeg"))
                .isInstanceOf(FileTooLargeException.class);

        verifyNoInteractions(fileStorage);
        verifyNoInteractions(storedFileMapper);
    }

    @Test
    void storeRejectsUnsupportedMediaType() {
        byte[] content = new byte[]{0x47, 0x49, 0x46, 0x38};
        when(inspector.inspect(content, "anim.gif", null))
                .thenThrow(new FileMediaTypeUnsupportedException("image/gif"));

        assertThatThrownBy(() -> service.store(householdId, content, "anim.gif", null))
                .isInstanceOf(FileMediaTypeUnsupportedException.class);

        verifyNoInteractions(fileStorage);
        verifyNoInteractions(storedFileMapper);
    }

    private StoredFileEntity updatedEntity(UUID id, UUID householdId, String storageKey, int refCount) {
        var e = new StoredFileEntity();
        e.setId(id);
        e.setHouseholdId(householdId);
        e.setStorageKey(storageKey);
        e.setReferenceCount(refCount);
        return e;
    }
}
