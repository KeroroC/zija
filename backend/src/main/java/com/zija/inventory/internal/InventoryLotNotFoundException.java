package com.zija.inventory.internal;

public class InventoryLotNotFoundException extends RuntimeException {
    public InventoryLotNotFoundException() {
        super();
    }

    public InventoryLotNotFoundException(String msg) {
        super(msg);
    }
}
