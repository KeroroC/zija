package com.zija.file;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 附件永久删除（物理清除）的公开事件。
 * <p>
 * 消费者（ai 等）据此清除全部依赖该附件的派生数据（例如知识来源选择与向量分块）。
 * 显式永久删除与保留期满的定时清除都会发布本事件。字段只追加、不重排、不删除——跨模块契约。
 */
public record AttachmentPurgedEvent(
        UUID eventId,
        UUID householdId,
        UUID fileId,
        OffsetDateTime businessTime
) {}
