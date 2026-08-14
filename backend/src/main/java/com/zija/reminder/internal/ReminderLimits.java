package com.zija.reminder.internal;

import java.util.List;

/**
 * 提醒规则边界与默认值常量。
 */
public final class ReminderLimits {

    /** 单档提醒天数下限（含）。 */
    public static final short MIN_EXPIRY_REMINDER_DAYS = 1;

    /** 单档提醒天数上限（含）。 */
    public static final short MAX_EXPIRY_REMINDER_DAYS = 3650;

    /** 提醒任务 snooze 目标时间至少晚于当前 1 分钟。 */
    public static final long MIN_SNOOZE_FUTURE_MINUTES = 1;

    /** 提醒任务 snooze 目标时间最多延后 10 年。 */
    public static final long MAX_SNOOZE_DAYS = 3650;

    /** 家庭默认提醒规则：30/7/1 天前提醒（规格默认值）。 */
    public static final List<Short> DEFAULT_EXPIRY_REMINDER_DAYS =
            List.of((short) 30, (short) 7, (short) 1);

    private ReminderLimits() {
    }
}
