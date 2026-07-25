package com.zija.inventory.internal;

public class InventoryReversalNotAllowedException extends RuntimeException {
    public InventoryReversalNotAllowedException() {
        super();
    }

    public InventoryReversalNotAllowedException(String msg) {
        super(msg);
    }
}
