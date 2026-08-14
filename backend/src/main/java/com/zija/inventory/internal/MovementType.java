package com.zija.inventory.internal;

/**
 * 库存移动类型常量。
 * <p>
 * 适用于 {@code movement.type} 字段及对应 {@code StockChangedEvent} 的
 * {@code movementType}。注意：{@code StockMapper.xml} / {@code ConsistencyCheckMapper.xml}
 * 中同样以字符串引用这些值，修改时必须同步。
 */
public final class MovementType {

    /** 入库。 */
    public static final String INBOUND = "INBOUND";

    /** 出库（消耗）。 */
    public static final String CONSUME = "CONSUME";

    /** 报损。 */
    public static final String LOSS = "LOSS";

    /** 调拨（位置间转移）。 */
    public static final String TRANSFER = "TRANSFER";

    /** 盘点调整。 */
    public static final String ADJUSTMENT = "ADJUSTMENT";

    /** 冲正（对原移动的反向记录）。 */
    public static final String REVERSAL = "REVERSAL";

    private MovementType() {
    }
}
