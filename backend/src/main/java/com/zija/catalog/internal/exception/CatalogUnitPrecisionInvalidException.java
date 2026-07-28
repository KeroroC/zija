package com.zija.catalog.internal.exception;

public class CatalogUnitPrecisionInvalidException extends RuntimeException {
    public CatalogUnitPrecisionInvalidException(int scale, int maxScale) {
        super("threshold precision " + scale + " exceeds unit decimal_scale " + maxScale);
    }
}
