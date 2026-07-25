package com.zija.inventory.internal;

public class InventoryMovementAlreadyReversedException extends RuntimeException {
    public InventoryMovementAlreadyReversedException() {
        super();
    }

    public InventoryMovementAlreadyReversedException(String msg) {
        super(msg);
    }
}
