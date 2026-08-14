package com.zija.identity.internal;

/**
 * 账户状态常量。
 * <p>
 * 适用于 {@code account.status} 字段，与家庭成员状态
 * {@link com.zija.shared.ZijaMemberStatus} 相互独立。
 */
public final class AccountStatus {

    /** 正常使用。 */
    public static final String ACTIVE = "ACTIVE";

    /** 已禁用（无法登录）。 */
    public static final String DISABLED = "DISABLED";

    private AccountStatus() {
    }
}
