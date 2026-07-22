package com.zija.catalog.internal;

public class CatalogDictionaryNameExistsException extends RuntimeException {
    public CatalogDictionaryNameExistsException(String name) {
        super("dictionary name already exists: " + name);
    }
}
