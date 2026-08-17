package com.zija.file;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 文件模块公共 API：附件（带挂载点的家庭文档）的存储、查询与生命周期管理。
 *
 * <p>一份附件 1:1 对应一条存储记录，任一时刻恰好挂在家庭 / 物品 / 批次之一。
 * 删除进入回收站（保留期内可恢复），物理删除只由清除任务在保留期满后执行。
 * 封面指定不增加引用——活着 = 记录存在且未过保留期物理删除。</p>
 */
public interface FileApi {

    /** 挂载点类型：家庭。 */
    String MOUNT_HOUSEHOLD = "HOUSEHOLD";
    /** 挂载点类型：物品。 */
    String MOUNT_ITEM = "ITEM";
    /** 挂载点类型：批次。 */
    String MOUNT_LOT = "LOT";

    /** 可作为封面指定的图片媒体类型。 */
    String COVER_MEDIA_TYPE_JPEG = "image/jpeg";
    String COVER_MEDIA_TYPE_PNG = "image/png";
    String COVER_MEDIA_TYPE_WEBP = "image/webp";

    /**
     * 存储文件内容并挂到指定挂载点（上传必须带挂载点，禁止无挂载孤儿）。
     * 同一挂载点下未删除附件名字不可重复，撞名抛 {@code FileNameDuplicateException}。
     */
    AttachmentInfo store(
            UUID householdId,
            byte[] content,
            String originalFilename,
            String declaredMediaType,
            String mountType,
            UUID mountId
    );

    /** 查询附件元信息（含存储键，供内容下载）。未物理删除（含回收站内）均可查到；不存在返回空。 */
    Optional<StoredFileInfo> findInfo(UUID householdId, UUID fileId);

    /**
     * 读取附件原始字节内容（供知识来源抽取等派生处理使用的安全公共契约）。
     *
     * <p>跨模块消费者不得直接接触文件卷或本模块内部存储类型，必须经此契约读取。
     * 附件不存在或不属于该家庭返回空；物理文件读取失败抛出运行时异常。
     * 回收站内未物理删除的附件同样可读（是否可用于派生用途由调用方按业务规则判断）。</p>
     */
    Optional<byte[]> readContent(UUID householdId, UUID fileId);

    /** 查询附件信息（不含存储键）。不存在或不属于该家庭返回空。 */
    Optional<AttachmentInfo> findAttachment(UUID householdId, UUID fileId);

    /**
     * 分页列出当前家庭的附件。默认只列未删除；{@code recycled=true} 列出回收站。
     * 可按挂载类型、挂载 UUID、名字子串筛选。列表不暴露存储键。
     */
    AttachmentPage list(
            UUID householdId,
            int page,
            int pageSize,
            String mountType,
            UUID mountId,
            String q,
            boolean recycled
    );

    /** 列出某挂载点下的未删除附件（物品/批次详情的窄接口）。 */
    List<AttachmentInfo> listByMount(UUID householdId, String mountType, UUID mountId);

    /** 修改附件展示名（只改展示名，不改对象键）。同一挂载点、未删除附件中名字必须唯一。 */
    AttachmentInfo rename(UUID householdId, UUID fileId, String name);

    /** 把附件送进回收站（设置删除时间，物理对象保留）。已删除则幂等返回。 */
    AttachmentInfo recycle(UUID householdId, UUID fileId);

    /**
     * 把附件送进回收站但不发布 {@link AttachmentRecycledEvent}（其余与 {@link #recycle}
     * 相同：设置删除时间、审计、已删除幂等返回）。
     *
     * <p>供调用方在同一事务内立刻以新附件替换该附件的场景（如换封面）使用：监听器若
     * 在替换完成前观察到「附件离开」会清除挂载点状态并改写版本，破坏紧随其后的
     * 乐观锁写入。静默回收不广播事件，但同一事务内调用方随后的失败仍会整体回滚。</p>
     */
    AttachmentInfo recycleSilently(UUID householdId, UUID fileId);

    /** 恢复回收站附件：清除删除标记，挂载点保持删除前的值；恢复后是普通附件，不恢复封面指定。 */
    AttachmentInfo restore(UUID householdId, UUID fileId);

    /**
     * 永久删除回收站附件：立即物理删除（卷上对象 + 数据库行），跳过保留期，不可恢复。
     *
     * <p>任何成员可执行，与回收/恢复同级；成功后记审计 {@code FILE_PURGED}，不发布公开领域事件。</p>
     *
     * @return true 表示已删除；附件不存在或不属于该家庭返回 false（调用方映射为 404）
     * @throws com.zija.file.exception.FileNotInRecycleBinException
     *         附件未在回收站（活附件，或并发中被恢复）时抛 409
     */
    boolean purge(UUID householdId, UUID fileId);

    /** 改挂附件到新的挂载点。目标挂载点校验由调用方（catalog/inventory/本模块 HTTP）负责。 */
    AttachmentInfo remount(UUID householdId, UUID fileId, String mountType, UUID mountId);

    /**
     * 物理清除回收站中已过保留期的附件（删元数据 + 卷上对象）。
     * 供定时任务与测试直接调用。
     *
     * @return 清除的附件数
     */
    int purgeExpired(OffsetDateTime before);

    /** 已存储文件的元信息（含存储键，仅供内容下载等内部用途）。 */
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
            OffsetDateTime createdAt,
            OffsetDateTime deletedAt
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
