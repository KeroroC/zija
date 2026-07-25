package com.zija.inventory.internal;

public class InventoryReversalWouldNegativeException extends RuntimeException {
    public InventoryReversalWouldNegativeException() {
        super();
    }

    public InventoryReversalWouldNegativeException(String msg) {
        super(msg);
    }
}
