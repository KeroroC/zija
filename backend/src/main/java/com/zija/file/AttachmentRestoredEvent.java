package com.zija.file;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 附件从回收站恢复的公开事件（恢复后回到删除前的挂载点）。
 * <p>
 * 消费者（ai 等）据此重新准备依赖该附件的派生状态（例如知识来源重新进入处理中）。
 * 字段只追加、不重排、不删除——跨模块契约。
 */
public record AttachmentRestoredEvent(
        UUID eventId,
        UUID householdId,
        UUID fileId,
        String mountType,
        UUID mountId,
        OffsetDateTime businessTime
) {}
