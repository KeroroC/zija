package com.zija.reminder.internal;

/**
 * 提醒任务状态（status）常量。
 * <p>
 * 适用于 {@code reminder_task.status} 字段。注意：{@code TaskMapper.xml}
 * 中同样以字符串引用这些值，修改时必须同步。
 */
public final class TaskStatus {

    /** 待处理。 */
    public static final String OPEN = "OPEN";

    /** 已暂停（snooze 到期后自动回到 OPEN）。 */
    public static final String SNOOZED = "SNOOZED";

    /** 已完成。 */
    public static final String DONE = "DONE";

    /** 已忽略。 */
    public static final String IGNORED = "IGNORED";

    private TaskStatus() {
    }
}
