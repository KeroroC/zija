package com.zija.shared;

/**
 * 家庭成员 / 账户状态常量。
 * <p>
 * 适用于 household 模块成员记录与 identity 模块账户记录的 {@code status} 字段，
 * 二者共享同一取值域。
 */
public final class ZijaMemberStatus {

    /** 正常：可登录、可参与家庭业务。 */
    public static final String ACTIVE = "ACTIVE";

    /** 已停用：被管理员停用，登录与业务操作均被拒绝。 */
    public static final String DEACTIVATED = "DEACTIVATED";

    private ZijaMemberStatus() {
    }
}
