package com.zija.shared;

/**
 * 领域变更类型常量。
 * <p>
 * 适用于 catalog / location 模块发布领域事件（{@code *ChangedEvent}）时的
 * {@code changeType} 字段，以及 reporting / reminder 等消费方对事件类型的判断。
 */
public final class ZijaChangeType {

    /** 新建。 */
    public static final String CREATED = "CREATED";

    /** 内容更新。 */
    public static final String UPDATED = "UPDATED";

    /** 归档（软删除）。 */
    public static final String ARCHIVED = "ARCHIVED";

    /** 从归档恢复。 */
    public static final String RESTORED = "RESTORED";

    /** 位置移动（仅位置/分类等树形记录）。 */
    public static final String MOVED = "MOVED";

    /** 删除。 */
    public static final String DELETED = "DELETED";

    /** 重命名（仅位置）。 */
    public static final String RENAMED = "RENAMED";

    private ZijaChangeType() {
    }
}
