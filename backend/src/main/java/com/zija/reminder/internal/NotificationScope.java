package com.zija.reminder.internal;

/**
 * 通知 scope 常量。
 * <p>
 * 适用于 {@code reminder_notification.scope} 字段。
 */
public final class NotificationScope {

    /** 提醒任务新建。 */
    public static final String TASK_CREATED = "TASK_CREATED";

    /** 提醒任务自动关闭。 */
    public static final String TASK_CLOSED = "TASK_CLOSED";

    /** 家庭默认提醒规则更新。 */
    public static final String RULE_CHANGED = "RULE_CHANGED";

    private NotificationScope() {
    }
}
