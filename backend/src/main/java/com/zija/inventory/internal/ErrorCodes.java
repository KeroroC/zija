package com.zija.inventory.internal;

/**
 * inventory 模块错误码常量。
 * <p>
 * 仅本模块内使用；跨模块共享的错误码见 {@link com.zija.shared.ZijaErrorCodes}。
 */
public final class ErrorCodes {

    public static final String INVENTORY_ARCHIVED_ITEM = "INVENTORY_ARCHIVED_ITEM";
    public static final String INVENTORY_IDEMPOTENCY_CONFLICT = "INVENTORY_IDEMPOTENCY_CONFLICT";
    public static final String INVENTORY_INSUFFICIENT_STOCK = "INVENTORY_INSUFFICIENT_STOCK";
    public static final String INVENTORY_LOT_NOT_FOUND = "INVENTORY_LOT_NOT_FOUND";
    public static final String INVENTORY_LOT_VERSION_CONFLICT = "INVENTORY_LOT_VERSION_CONFLICT";
    public static final String INVENTORY_MOVEMENT_ALREADY_REVERSED = "INVENTORY_MOVEMENT_ALREADY_REVERSED";
    public static final String INVENTORY_QUANTITY_PRECISION_INVALID = "INVENTORY_QUANTITY_PRECISION_INVALID";
    public static final String INVENTORY_REVERSAL_NOT_ALLOWED = "INVENTORY_REVERSAL_NOT_ALLOWED";
    public static final String INVENTORY_REVERSAL_WOULD_NEGATIVE = "INVENTORY_REVERSAL_WOULD_NEGATIVE";
    public static final String INVENTORY_STOCKTAKE_NOT_DRAFT = "INVENTORY_STOCKTAKE_NOT_DRAFT";
    public static final String INVENTORY_STOCKTAKE_STALE = "INVENTORY_STOCKTAKE_STALE";

    private ErrorCodes() {
    }
}
