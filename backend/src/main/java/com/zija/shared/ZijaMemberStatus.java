package com.zija.shared;

/**
 * 家庭成员状态常量。
 * <p>
 * 仅适用于 household 模块成员记录的 {@code status} 字段。
 * identity 模块账户状态使用独立的 AccountStatus（ACTIVE/DISABLED），
 * 与成员状态（ACTIVE/DEACTIVATED）取值域不同，不可混用。
 */
public final class ZijaMemberStatus {

    /** 正常：可登录、可参与家庭业务。 */
    public static final String ACTIVE = "ACTIVE";

    /** 已停用：被管理员停用，登录与业务操作均被拒绝。 */
    public static final String DEACTIVATED = "DEACTIVATED";

    private ZijaMemberStatus() {
    }
}
