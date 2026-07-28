package com.zija.catalog.internal.exception;

public class CatalogCycleDetectedException extends RuntimeException {
    public CatalogCycleDetectedException() {
        super("category move would create a cycle");
    }
}
