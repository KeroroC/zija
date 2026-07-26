package com.zija.reminder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 提醒模块公共只读端口：任务与首页聚合只读 DTO。
 * 规则读写、任务状态机操作、通知写操作由本模块 REST 端点接收，不在此端口。
 */
public interface ReminderApi {

    /** 返回家庭「优先任务」前 N 条（按 severity URGENT>WARN>INFO 再按 due_at ASC）。 */
    List<PriorityTaskInfo> priorityTasks(UUID householdId, int topN);

    record PriorityTaskInfo(
            UUID taskId,
            String kind,            // EXPIRY | LOW_STOCK
            String severity,        // INFO | WARN | URGENT
            String title,
            OffsetDateTime dueAt,
            UUID itemId,
            UUID lotId
    ) {}
}
