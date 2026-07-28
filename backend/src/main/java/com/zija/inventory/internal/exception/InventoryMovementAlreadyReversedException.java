package com.zija.inventory.internal.exception;

public class InventoryMovementAlreadyReversedException extends RuntimeException {
    public InventoryMovementAlreadyReversedException() {
        super();
    }

    public InventoryMovementAlreadyReversedException(String msg) {
        super(msg);
    }
}
