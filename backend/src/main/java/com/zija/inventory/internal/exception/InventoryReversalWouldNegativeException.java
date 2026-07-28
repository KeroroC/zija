package com.zija.inventory.internal.exception;

public class InventoryReversalWouldNegativeException extends RuntimeException {
    public InventoryReversalWouldNegativeException() {
        super();
    }

    public InventoryReversalWouldNegativeException(String msg) {
        super(msg);
    }
}
