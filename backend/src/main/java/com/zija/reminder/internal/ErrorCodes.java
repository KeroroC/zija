package com.zija.reminder.internal;

/**
 * reminder 模块错误码常量。
 * <p>
 * 仅本模块内使用；跨模块共享的错误码见 {@link com.zija.shared.ZijaErrorCodes}。
 */
public final class ErrorCodes {

    public static final String MAIL_SETTING_VERSION_CONFLICT = "MAIL_SETTING_VERSION_CONFLICT";
    public static final String REMINDER_INVALID_ARGUMENT = "REMINDER_INVALID_ARGUMENT";
    public static final String REMINDER_RULE_EXPIRY_DAYS_INVALID = "REMINDER_RULE_EXPIRY_DAYS_INVALID";
    public static final String REMINDER_RULE_LOW_STOCK_INVALID = "REMINDER_RULE_LOW_STOCK_INVALID";
    public static final String REMINDER_RULE_NOT_INITIALIZED = "REMINDER_RULE_NOT_INITIALIZED";
    public static final String REMINDER_RULE_VERSION_CONFLICT = "REMINDER_RULE_VERSION_CONFLICT";
    public static final String REMINDER_TASK_INVALID_TRANSITION = "REMINDER_TASK_INVALID_TRANSITION";
    public static final String REMINDER_TASK_NOT_FOUND = "REMINDER_TASK_NOT_FOUND";
    public static final String REMINDER_TASK_SNOOZE_UNTIL_INVALID = "REMINDER_TASK_SNOOZE_UNTIL_INVALID";

    private ErrorCodes() {
    }
}
