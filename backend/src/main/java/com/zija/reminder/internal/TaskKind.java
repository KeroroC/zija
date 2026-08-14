package com.zija.reminder.internal;

/**
 * 提醒任务类型（kind）常量。
 * <p>
 * 适用于 {@code reminder_task.kind} 字段。注意：{@code TaskMapper.xml}
 * 中同样以字符串引用这些值，修改时必须同步。
 */
public final class TaskKind {

    /** 临期提醒。 */
    public static final String EXPIRY = "EXPIRY";

    /** 低库存提醒。 */
    public static final String LOW_STOCK = "LOW_STOCK";

    private TaskKind() {
    }
}
