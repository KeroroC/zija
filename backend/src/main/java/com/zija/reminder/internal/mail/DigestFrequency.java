package com.zija.reminder.internal.mail;

/**
 * 邮件摘要发送频率常量。
 * <p>
 * 适用于 {@code mail_setting.digest_frequency} 字段。
 */
public final class DigestFrequency {

    /** 每日发送。 */
    public static final String DAILY = "DAILY";

    /** 每周发送。 */
    public static final String WEEKLY = "WEEKLY";

    private DigestFrequency() {
    }
}
