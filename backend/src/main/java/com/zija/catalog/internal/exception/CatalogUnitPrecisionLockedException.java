package com.zija.catalog.internal.exception;

public class CatalogUnitPrecisionLockedException extends RuntimeException {
    public CatalogUnitPrecisionLockedException() {
        super("unit precision cannot be changed after items reference it");
    }
}
