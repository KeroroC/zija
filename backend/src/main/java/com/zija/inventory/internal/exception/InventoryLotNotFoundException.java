package com.zija.inventory.internal.exception;

public class InventoryLotNotFoundException extends RuntimeException {
    public InventoryLotNotFoundException() {
        super();
    }

    public InventoryLotNotFoundException(String msg) {
        super(msg);
    }
}
