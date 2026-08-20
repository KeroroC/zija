package com.zija.file.internal;

import com.zija.file.FileApi;
import com.zija.file.internal.event.FileEventPublisher;
import com.zija.file.exception.FileMediaTypeUnsupportedException;
import com.zija.file.exception.FileNameDuplicateException;
import com.zija.file.exception.FileNotAvailableException;
import com.zija.file.exception.FileNotInRecycleBinException;
import com.zija.file.exception.FileTooLargeException;
import com.zija.file.internal.persistence.StoredFileEntity;
import com.zija.file.internal.persistence.StoredFileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FileServiceTest {

    private StoredFileMapper storedFileMapper;
    private FileContentInspector inspector;
    private FileStorage fileStorage;
    private FileEventPublisher eventPublisher;
    private SystemApiStub systemApi;
    private FileService service;

    private final UUID householdId = UUID.randomUUID();

    /** 记录审计事件的桩（SystemApi 为接口，避免 Mockito 丢失调用记录）。 */
    private static class SystemApiStub implements com.zija.system.SystemApi {
        java.util.List<String> actions = new java.util.ArrayList<>();
        @Override public void recordAudit(AuditEvent event) { actions.add(event.action()); }
        @Override public SystemSnapshot current() { return null; }
        @Override public AuditLogPage queryAuditLogs(UUID householdId, OffsetDateTime from, OffsetDateTime to,
                                                     String action, UUID actorAccountId, String outcome,
                                                     int page, int pageSize) { return null; }
    }

    @BeforeEach
    void setUp() {
        storedFileMapper = mock(StoredFileMapper.class);
        inspector = mock(FileContentInspector.class);
        fileStorage = mock(FileStorage.class);
        eventPublisher = mock(FileEventPublisher.class);
        systemApi = new SystemApiStub();
        service = new FileService(storedFileMapper, inspector, fileStorage, systemApi, eventPublisher);
    }

    @Test
    void storeCallsInspectorThenFileStorageThenMapperInsert() throws IOException {
        byte[] content = new byte[]{1, 2, 3};
        var inspectionResult = new FileContentInspector.InspectionResult(
                "image/jpeg", "photo.jpg", "abc123hash");
        when(inspector.inspect(content, "photo.jpg", "image/jpeg")).thenReturn(inspectionResult);
        when(fileStorage.store(content, ".jpg")).thenReturn("2026/07/uuid.jpg");
        when(storedFileMapper.selectCount(any())).thenReturn(0L);

        var result = service.store(householdId, content, "photo.jpg", "image/jpeg",
                FileApi.MOUNT_HOUSEHOLD, householdId);

        var order = inOrder(inspector, fileStorage, storedFileMapper);
        order.verify(inspector).inspect(content, "photo.jpg", "image/jpeg");
        order.verify(fileStorage).store(content, ".jpg");
        order.verify(storedFileMapper).insert(any(StoredFileEntity.class));

        assertThat(result.householdId()).isEqualTo(householdId);
        assertThat(result.name()).isEqualTo("photo.jpg");
        assertThat(result.mediaType()).isEqualTo("image/jpeg");
        assertThat(result.mountType()).isEqualTo("HOUSEHOLD");
        assertThat(result.mountId()).isEqualTo(householdId);
        assertThat(systemApi.actions).contains(com.zija.system.SystemApi.AuditAction.FILE_UPLOADED);
    }

    @Test
    void storeDeletesWrittenFileWhenDbInsertFails() throws IOException {
        byte[] content = new byte[]{1, 2, 3};
        when(inspector.inspect(content, "photo.jpg", "image/jpeg"))
                .thenReturn(new FileContentInspector.InspectionResult("image/jpeg", "photo.jpg", "h"));
        when(fileStorage.store(content, ".jpg")).thenReturn("2026/07/uuid.jpg");
        when(storedFileMapper.selectCount(any())).thenReturn(0L);
        doThrow(new RuntimeException("db down")).when(storedFileMapper).insert(any(StoredFileEntity.class));

        assertThatThrownBy(() -> service.store(householdId, content, "photo.jpg", "image/jpeg",
                FileApi.MOUNT_HOUSEHOLD, householdId))
                .isInstanceOf(RuntimeException.class);

        // 落盘成功、DB insert 失败：必须删除已写的卷对象，避免孤儿文件
        verify(fileStorage).delete("2026/07/uuid.jpg");
    }

    @Test
    void storeRejectsDuplicateNameOnSameMount() {
        byte[] content = new byte[]{1, 2, 3};
        when(inspector.inspect(content, "photo.jpg", "image/jpeg"))
                .thenReturn(new FileContentInspector.InspectionResult("image/jpeg", "photo.jpg", "h"));
        when(storedFileMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.store(householdId, content, "photo.jpg", "image/jpeg",
                FileApi.MOUNT_HOUSEHOLD, householdId))
                .isInstanceOf(FileNameDuplicateException.class);

        verifyNoInteractions(fileStorage);
    }

    @Test
    void recycleSetsDeletedAtAndPublishesEvent() {
        UUID fileId = UUID.randomUUID();
        var entity = entity(fileId, householdId, "2026/07/uuid.jpg", "photo.jpg", "image/jpeg", null);
        when(storedFileMapper.selectById(fileId)).thenReturn(entity);

        var result = service.recycle(householdId, fileId);

        assertThat(result.deletedAt()).isNotNull();
        verify(storedFileMapper).updateById(entity);
        verify(eventPublisher).publishRecycled(eq(householdId), eq(fileId), eq("HOUSEHOLD"), eq(householdId));
        assertThat(systemApi.actions).contains(com.zija.system.SystemApi.AuditAction.FILE_DELETED);
    }

    @Test
    void recycleIsIdempotentForAlreadyRecycled() {
        UUID fileId = UUID.randomUUID();
        var entity = entity(fileId, householdId, "2026/07/uuid.jpg", "photo.jpg", "image/jpeg",
                OffsetDateTime.now());
        when(storedFileMapper.selectById(fileId)).thenReturn(entity);

        var result = service.recycle(householdId, fileId);

        assertThat(result.deletedAt()).isNotNull();
        verify(storedFileMapper, never()).updateById(any(StoredFileEntity.class));
        verify(eventPublisher, never()).publishRecycled(any(), any(), any(), any());
    }

    @Test
    void recycleSilentlySetsDeletedAtWithoutPublishingEvent() {
        UUID fileId = UUID.randomUUID();
        var entity = entity(fileId, householdId, "2026/07/uuid.jpg", "photo.jpg", "image/jpeg", null);
        when(storedFileMapper.selectById(fileId)).thenReturn(entity);

        var result = service.recycleSilently(householdId, fileId);

        assertThat(result.deletedAt()).isNotNull();
        verify(storedFileMapper).updateById(entity);
        verify(eventPublisher, never()).publishRecycled(any(), any(), any(), any());
        assertThat(systemApi.actions).contains(com.zija.system.SystemApi.AuditAction.FILE_DELETED);
    }

    @Test
    void recycleSilentlyIsIdempotentForAlreadyRecycled() {
        UUID fileId = UUID.randomUUID();
        var entity = entity(fileId, householdId, "2026/07/uuid.jpg", "photo.jpg", "image/jpeg",
                OffsetDateTime.now());
        when(storedFileMapper.selectById(fileId)).thenReturn(entity);

        var result = service.recycleSilently(householdId, fileId);

        assertThat(result.deletedAt()).isNotNull();
        verify(storedFileMapper, never()).updateById(any(StoredFileEntity.class));
        verify(eventPublisher, never()).publishRecycled(any(), any(), any(), any());
    }

    @Test
    void restoreClearsDeletedAtAndKeepsMount() {
        UUID fileId = UUID.randomUUID();
        var entity = entity(fileId, householdId, "2026/07/uuid.jpg", "photo.jpg", "image/jpeg",
                OffsetDateTime.now());
        entity.setMountType(FileApi.MOUNT_ITEM);
        entity.setMountId(UUID.randomUUID());
        entity.setNameNormalized("photo.jpg");
        when(storedFileMapper.selectById(fileId)).thenReturn(entity);
        when(storedFileMapper.selectCount(any())).thenReturn(0L);
        when(storedFileMapper.restoreIfRecycled(householdId, fileId)).thenReturn(1);

        var result = service.restore(householdId, fileId);

        assertThat(result.deletedAt()).isNull();
        assertThat(result.mountType()).isEqualTo(FileApi.MOUNT_ITEM);
        verify(storedFileMapper).restoreIfRecycled(householdId, fileId);
        assertThat(systemApi.actions).contains(com.zija.system.SystemApi.AuditAction.FILE_RESTORED);
    }

    @Test
    void restoreRejectsNameTakenAtMountPoint() {
        UUID fileId = UUID.randomUUID();
        var entity = entity(fileId, householdId, "2026/07/uuid.jpg", "photo.jpg", "image/jpeg",
                OffsetDateTime.now());
        entity.setMountType(FileApi.MOUNT_HOUSEHOLD);
        entity.setMountId(householdId);
        entity.setNameNormalized("photo.jpg");
        when(storedFileMapper.selectById(fileId)).thenReturn(entity);
        when(storedFileMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.restore(householdId, fileId))
                .isInstanceOf(FileNameDuplicateException.class);
    }

    @Test
    void remountUpdatesMountAndPublishesMovedEvent() {
        UUID fileId = UUID.randomUUID();
        UUID oldItemId = UUID.randomUUID();
        var entity = entity(fileId, householdId, "2026/07/uuid.jpg", "photo.jpg", "image/jpeg", null);
        entity.setMountType(FileApi.MOUNT_ITEM);
        entity.setMountId(oldItemId);
        entity.setNameNormalized("photo.jpg");
        when(storedFileMapper.selectById(fileId)).thenReturn(entity);
        when(storedFileMapper.selectCount(any())).thenReturn(0L);

        var result = service.remount(householdId, fileId, FileApi.MOUNT_LOT, UUID.randomUUID());

        assertThat(result.mountType()).isEqualTo(FileApi.MOUNT_LOT);
        verify(storedFileMapper).updateById(entity);
        verify(eventPublisher).publishMoved(eq(householdId), eq(fileId),
                eq(FileApi.MOUNT_ITEM), eq(oldItemId), eq(FileApi.MOUNT_LOT), any());
        assertThat(systemApi.actions).contains(com.zija.system.SystemApi.AuditAction.FILE_MOVED);
    }

    @Test
    void remountRejectsRecycledAttachment() {
        UUID fileId = UUID.randomUUID();
        var entity = entity(fileId, householdId, "2026/07/uuid.jpg", "photo.jpg", "image/jpeg",
                OffsetDateTime.now());
        entity.setMountType(FileApi.MOUNT_HOUSEHOLD);
        entity.setMountId(householdId);
        when(storedFileMapper.selectById(fileId)).thenReturn(entity);

        assertThatThrownBy(() -> service.remount(householdId, fileId, FileApi.MOUNT_ITEM, UUID.randomUUID()))
                .isInstanceOf(FileNotAvailableException.class);
    }

    @Test
    void renameSanitizesUnsafeCharactersBeforeStoring() {
        UUID fileId = UUID.randomUUID();
        var entity = entity(fileId, householdId, "2026/07/uuid.jpg", "old.jpg", "image/jpeg", null);
        when(storedFileMapper.selectById(fileId)).thenReturn(entity);
        when(storedFileMapper.selectCount(any())).thenReturn(0L);

        // 双引号与 NUL 会被清洗；路径分隔符只保留最后一段
        var result = service.rename(householdId, fileId, "a\"b\u0000c.txt");

        assertThat(result.name()).isEqualTo("abc.txt");
        assertThat(entity.getOriginalFilename()).isEqualTo("abc.txt");
        assertThat(entity.getNameNormalized()).isEqualTo("abc.txt");
        verify(storedFileMapper).updateById(entity);
        assertThat(systemApi.actions).contains(com.zija.system.SystemApi.AuditAction.FILE_RENAMED);
    }

    @Test
    void renameRejectsNameThatSanitizesToEmpty() {
        UUID fileId = UUID.randomUUID();
        var entity = entity(fileId, householdId, "2026/07/uuid.jpg", "old.jpg", "image/jpeg", null);
        when(storedFileMapper.selectById(fileId)).thenReturn(entity);

        assertThatThrownBy(() -> service.rename(householdId, fileId, "\"\"\""))
                .isInstanceOf(ResponseStatusException.class);

        verify(storedFileMapper, never()).updateById(any(StoredFileEntity.class));
        assertThat(systemApi.actions).doesNotContain(com.zija.system.SystemApi.AuditAction.FILE_RENAMED);
    }

    @Test
    void purgeExpiredDeletesRowThenStorage() throws IOException {
        UUID fileId = UUID.randomUUID();
        var expired = entity(fileId, householdId, "2026/07/old.jpg", "old.jpg", "image/jpeg",
                OffsetDateTime.now().minusDays(40));
        when(storedFileMapper.findExpired(any())).thenReturn(List.of(expired));
        when(storedFileMapper.delete(any())).thenReturn(1);

        int purged = service.purgeExpired(OffsetDateTime.now().minusDays(30));

        assertThat(purged).isEqualTo(1);
        // 与 purge() 同一顺序：先认领删除 DB 行，再删卷对象；
        // 崩溃窗口只留「无引用 + 文件仍在」的孤儿文件，不留「引用仍在 + 文件已删」的悬空行。
        var order = inOrder(storedFileMapper, fileStorage);
        order.verify(storedFileMapper).delete(any());
        order.verify(fileStorage).delete("2026/07/old.jpg");
        verify(storedFileMapper, never()).deleteById(any(UUID.class));
    }

    @Test
    void purgeExpiredReinsertsRowWhenStorageDeleteFails() throws IOException {
        UUID fileId = UUID.randomUUID();
        var expired = entity(fileId, householdId, "2026/07/old.jpg", "old.jpg", "image/jpeg",
                OffsetDateTime.now().minusDays(40));
        when(storedFileMapper.findExpired(any())).thenReturn(List.of(expired));
        when(storedFileMapper.delete(any())).thenReturn(1);
        doThrow(new IOException("disk")).when(fileStorage).delete(anyString());

        int purged = service.purgeExpired(OffsetDateTime.now().minusDays(30));

        assertThat(purged).isZero();
        // 行已被认领删除但卷对象删除失败：回插该行保留待重试，避免留下孤儿文件
        verify(storedFileMapper).insert(expired);
        verify(storedFileMapper, never()).deleteById(any(UUID.class));
    }

    @Test
    void purgeExpiredSkipsFileWhenRowNoLongerRecycled() throws IOException {
        UUID fileId = UUID.randomUUID();
        var expired = entity(fileId, householdId, "2026/07/old.jpg", "old.jpg", "image/jpeg",
                OffsetDateTime.now().minusDays(40));
        when(storedFileMapper.findExpired(any())).thenReturn(List.of(expired));
        // 认领删除返回 0 行：并发中已被恢复或清除，不得触碰卷对象，避免删掉已恢复活附件的内容
        when(storedFileMapper.delete(any())).thenReturn(0);

        int purged = service.purgeExpired(OffsetDateTime.now().minusDays(30));

        assertThat(purged).isZero();
        verify(fileStorage, never()).delete(anyString());
        verify(storedFileMapper, never()).insert(any(StoredFileEntity.class));
        verify(storedFileMapper, never()).deleteById(any(UUID.class));
    }

    @Test
    void purgeDeletesStorageThenRowAndAudits() throws IOException {
        UUID fileId = UUID.randomUUID();
        var entity = entity(fileId, householdId, "2026/07/uuid.jpg", "photo.jpg", "image/jpeg",
                OffsetDateTime.now().minusDays(1));
        when(storedFileMapper.selectById(fileId)).thenReturn(entity);
        when(storedFileMapper.delete(any())).thenReturn(1);

        boolean purged = service.purge(householdId, fileId);

        assertThat(purged).isTrue();
        // 认领（条件删除）必须先于卷对象删除：恢复先赢时不得触碰卷对象
        var order = inOrder(storedFileMapper, fileStorage);
        order.verify(storedFileMapper).delete(any());
        order.verify(fileStorage).delete("2026/07/uuid.jpg");
        assertThat(systemApi.actions).contains(com.zija.system.SystemApi.AuditAction.FILE_PURGED);
        verify(eventPublisher, never()).publishRecycled(any(), any(), any(), any());
        verify(eventPublisher, never()).publishMoved(any(), any(), any(), any(), any(), any());
    }

    @Test
    void purgeRejectsLiveAttachment() throws IOException {
        UUID fileId = UUID.randomUUID();
        var entity = entity(fileId, householdId, "2026/07/uuid.jpg", "photo.jpg", "image/jpeg", null);
        when(storedFileMapper.selectById(fileId)).thenReturn(entity);

        assertThatThrownBy(() -> service.purge(householdId, fileId))
                .isInstanceOf(FileNotInRecycleBinException.class);

        verify(fileStorage, never()).delete(anyString());
        verify(storedFileMapper, never()).delete(any());
        assertThat(systemApi.actions).doesNotContain(com.zija.system.SystemApi.AuditAction.FILE_PURGED);
    }

    @Test
    void purgeKeepsRowWhenStorageDeleteFails() throws IOException {
        UUID fileId = UUID.randomUUID();
        var entity = entity(fileId, householdId, "2026/07/uuid.jpg", "photo.jpg", "image/jpeg",
                OffsetDateTime.now().minusDays(1));
        when(storedFileMapper.selectById(fileId)).thenReturn(entity);
        when(storedFileMapper.delete(any())).thenReturn(1);
        doThrow(new IOException("disk")).when(fileStorage).delete(anyString());

        // 请求失败；生产环境中 @Transactional 抛异常回滚整笔（认领删除与审计一并撤销），行留在回收站交定时任务重试。
        // 审计在卷对象删除之前写入：卷删除失败时审计随事务回滚，不会留下「已记审计但删除失败」的记录。
        assertThatThrownBy(() -> service.purge(householdId, fileId))
                .isInstanceOf(RuntimeException.class);

        verify(storedFileMapper).delete(any());
        assertThat(systemApi.actions).contains(com.zija.system.SystemApi.AuditAction.FILE_PURGED);
    }

    @Test
    void purgeReturnsFalseWhenNotFound() throws IOException {
        UUID fileId = UUID.randomUUID();
        when(storedFileMapper.selectById(fileId)).thenReturn(null);

        boolean purged = service.purge(householdId, fileId);

        assertThat(purged).isFalse();
        verify(fileStorage, never()).delete(anyString());
        verify(storedFileMapper, never()).delete(any());
    }

    @Test
    void purgeThrowsConflictWhenRestoreWinsRace() throws IOException {
        UUID fileId = UUID.randomUUID();
        var entity = entity(fileId, householdId, "2026/07/uuid.jpg", "photo.jpg", "image/jpeg",
                OffsetDateTime.now().minusDays(1));
        when(storedFileMapper.selectById(fileId)).thenReturn(entity);
        when(storedFileMapper.delete(any())).thenReturn(0);

        assertThatThrownBy(() -> service.purge(householdId, fileId))
                .isInstanceOf(FileNotInRecycleBinException.class);

        // 恢复先赢：不触碰卷对象，避免已恢复的活附件内容被删
        verify(fileStorage, never()).delete(anyString());
        assertThat(systemApi.actions).doesNotContain(com.zija.system.SystemApi.AuditAction.FILE_PURGED);
    }

    @Test
    void storeRejectsOversizedContent() {
        byte[] content = new byte[5 * 1024 * 1024 + 1];
        when(inspector.inspect(content, "big.jpg", "image/jpeg"))
                .thenThrow(new FileTooLargeException(content.length));

        assertThatThrownBy(() -> service.store(householdId, content, "big.jpg", "image/jpeg",
                FileApi.MOUNT_HOUSEHOLD, householdId))
                .isInstanceOf(FileTooLargeException.class);

        verifyNoInteractions(fileStorage);
        verifyNoInteractions(storedFileMapper);
    }

    @Test
    void storeRejectsUnsupportedMediaType() {
        byte[] content = new byte[]{0x47, 0x49, 0x46, 0x38};
        when(inspector.inspect(content, "anim.gif", null))
                .thenThrow(new FileMediaTypeUnsupportedException("image/gif"));

        assertThatThrownBy(() -> service.store(householdId, content, "anim.gif", null,
                FileApi.MOUNT_HOUSEHOLD, householdId))
                .isInstanceOf(FileMediaTypeUnsupportedException.class);

        verifyNoInteractions(fileStorage);
        verifyNoInteractions(storedFileMapper);
    }

    private StoredFileEntity entity(UUID id, UUID householdId, String storageKey, String name,
                                    String mediaType, OffsetDateTime deletedAt) {
        var e = new StoredFileEntity();
        e.setId(id);
        e.setHouseholdId(householdId);
        e.setStorageKey(storageKey);
        e.setOriginalFilename(name);
        e.setDetectedMediaType(mediaType);
        e.setByteSize(10L);
        e.setSha256("hash");
        e.setCreatedAt(OffsetDateTime.now());
        e.setMountType(FileApi.MOUNT_HOUSEHOLD);
        e.setMountId(householdId);
        e.setNameNormalized(FileService.normalizeName(name));
        e.setDeletedAt(deletedAt);
        return e;
    }
}
