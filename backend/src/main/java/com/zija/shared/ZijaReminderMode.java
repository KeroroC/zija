package com.zija.shared;

/**
 * 物品级提醒配置模式常量。
 * <p>
 * 适用于物品的 {@code expiryReminderMode} 与 {@code lowStockMode} 字段，
 * 由 catalog 模块维护、reminder 模块解析。
 */
public final class ZijaReminderMode {

    /** 继承家庭默认规则。 */
    public static final String INHERIT = "INHERIT";

    /** 禁用提醒。 */
    public static final String DISABLED = "DISABLED";

    /** 物品级自定义。 */
    public static final String CUSTOM = "CUSTOM";

    private ZijaReminderMode() {
    }
}
