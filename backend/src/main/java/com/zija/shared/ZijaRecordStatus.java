package com.zija.shared;

/**
 * 记录生命周期状态常量。
 * <p>
 * 适用于目录物品、分类、品牌、单位、标签、位置等归档型记录的 {@code status} 字段，
 * 以及对应领域事件的 {@code changeType}。
 */
public final class ZijaRecordStatus {

    /** 活动中：正常可见、可参与业务。 */
    public static final String ACTIVE = "ACTIVE";

    /** 已归档：不再参与新业务，历史数据保留。 */
    public static final String ARCHIVED = "ARCHIVED";

    private ZijaRecordStatus() {
    }
}
