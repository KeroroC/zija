package com.zija.catalog.internal;

import java.util.UUID;

public class CatalogArchivedDictionaryException extends RuntimeException {
    public CatalogArchivedDictionaryException(String type, UUID id) {
        super("archived " + type + " cannot be used: " + id);
    }
}
