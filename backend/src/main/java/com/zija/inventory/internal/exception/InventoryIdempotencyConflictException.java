package com.zija.inventory.internal.exception;

public class InventoryIdempotencyConflictException extends RuntimeException {
    public InventoryIdempotencyConflictException() {
        super();
    }

    public InventoryIdempotencyConflictException(String msg) {
        super(msg);
    }
}
