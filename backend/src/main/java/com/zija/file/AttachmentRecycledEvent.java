package com.zija.file;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 附件进入回收站的公开事件。
 * <p>
 * 消费者（catalog 等）据此同步清理依赖该附件的业务状态（例如物品封面指定）。
 * 字段只追加、不重排、不删除——跨模块契约。
 */
public record AttachmentRecycledEvent(
        UUID eventId,
        UUID householdId,
        UUID fileId,
        String mountType,
        UUID mountId,
        OffsetDateTime businessTime
) {}
