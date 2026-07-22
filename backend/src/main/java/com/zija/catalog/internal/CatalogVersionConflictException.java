package com.zija.catalog.internal;

public class CatalogVersionConflictException extends RuntimeException {
    public CatalogVersionConflictException() {
        super("version conflict");
    }
}
