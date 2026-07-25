package com.zija.inventory.internal;

public class InventoryIdempotencyConflictException extends RuntimeException {
    public InventoryIdempotencyConflictException() {
        super();
    }

    public InventoryIdempotencyConflictException(String msg) {
        super(msg);
    }
}
