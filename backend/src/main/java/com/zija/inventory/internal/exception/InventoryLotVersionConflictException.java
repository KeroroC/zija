package com.zija.inventory.internal.exception;

public class InventoryLotVersionConflictException extends RuntimeException {
    public InventoryLotVersionConflictException() {
        super();
    }

    public InventoryLotVersionConflictException(String msg) {
        super(msg);
    }
}
