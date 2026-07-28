package com.zija.catalog.internal.exception;

public class CatalogVersionConflictException extends RuntimeException {
    public CatalogVersionConflictException() {
        super("version conflict");
    }
}
