package com.zija.file;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 附件改挂的公开事件（旧挂载点 → 新挂载点）。
 * <p>
 * 消费者（catalog 等）据此同步清理依赖旧挂载点的业务状态（例如物品封面指定）。
 * 字段只追加、不重排、不删除——跨模块契约。
 */
public record AttachmentMovedEvent(
        UUID eventId,
        UUID householdId,
        UUID fileId,
        String oldMountType,
        UUID oldMountId,
        String newMountType,
        UUID newMountId,
        OffsetDateTime businessTime
) {}
