package com.zija.inventory.internal.exception;

public class InventoryInsufficientStockException extends RuntimeException {
    public InventoryInsufficientStockException() {
        super();
    }

    public InventoryInsufficientStockException(String msg) {
        super(msg);
    }
}
