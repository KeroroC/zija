package com.zija.catalog.internal.exception;

public class CatalogCategoryHasChildrenException extends RuntimeException {
    public CatalogCategoryHasChildrenException() {
        super("category has active children");
    }
}
