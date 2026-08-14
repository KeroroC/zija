package com.zija.inventory.internal;

/**
 * 盘点单状态常量。
 * <p>
 * 适用于 {@code stocktake.status} 字段。注意：{@code StocktakeMapper.xml}
 * 中同样以字符串引用这些值，修改时必须同步。
 */
public final class StocktakeStatus {

    /** 草稿：可编辑条目。 */
    public static final String DRAFT = "DRAFT";

    /** 已取消。 */
    public static final String CANCELLED = "CANCELLED";

    /** 已完成：差异已生成调整移动。 */
    public static final String COMPLETED = "COMPLETED";

    private StocktakeStatus() {
    }
}
