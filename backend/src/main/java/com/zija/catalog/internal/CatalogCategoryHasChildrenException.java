package com.zija.catalog.internal;

public class CatalogCategoryHasChildrenException extends RuntimeException {
    public CatalogCategoryHasChildrenException() {
        super("category has active children");
    }
}
