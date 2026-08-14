package com.zija.shared;

/**
 * 家庭成员角色常量。
 * <p>
 * 适用于成员记录的 {@code role} 字段及通知收件人角色等派生场景。
 */
public final class ZijaMemberRole {

    /** 所有者：家庭创建者，拥有全部权限且不可被停用。 */
    public static final String OWNER = "OWNER";

    /** 管理员：可管理成员与字典，不可操作所有者。 */
    public static final String ADMIN = "ADMIN";

    /** 普通成员：仅可参与库存等日常业务。 */
    public static final String MEMBER = "MEMBER";

    private ZijaMemberRole() {
    }
}
