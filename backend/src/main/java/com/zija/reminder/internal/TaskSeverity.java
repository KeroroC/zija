package com.zija.reminder.internal;

/**
 * 提醒任务严重度（severity）常量。
 * <p>
 * 适用于 {@code reminder_task.severity} 字段。
 */
public final class TaskSeverity {

    /** 紧急（触发邮件）。 */
    public static final String URGENT = "URGENT";

    /** 警告。 */
    public static final String WARN = "WARN";

    /** 一般提示。 */
    public static final String INFO = "INFO";

    private TaskSeverity() {
    }
}
