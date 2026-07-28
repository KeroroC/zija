package com.zija.catalog.internal.exception;

import java.util.UUID;

public class CatalogArchivedDictionaryException extends RuntimeException {
    public CatalogArchivedDictionaryException(String type, UUID id) {
        super("archived " + type + " cannot be used: " + id);
    }
}
