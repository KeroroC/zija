package com.zija.catalog.internal;

/**
 * catalog 模块错误码常量。
 * <p>
 * 仅本模块内使用；跨模块共享的错误码见 {@link com.zija.shared.ZijaErrorCodes}。
 */
public final class ErrorCodes {

    public static final String CATALOG_ARCHIVED_DICTIONARY = "CATALOG_ARCHIVED_DICTIONARY";
    public static final String CATALOG_CATEGORY_HAS_CHILDREN = "CATALOG_CATEGORY_HAS_CHILDREN";
    public static final String CATALOG_CYCLE_DETECTED = "CATALOG_CYCLE_DETECTED";
    public static final String CATALOG_DICTIONARY_NAME_EXISTS = "CATALOG_DICTIONARY_NAME_EXISTS";
    public static final String CATALOG_UNIT_PRECISION_INVALID = "CATALOG_UNIT_PRECISION_INVALID";
    public static final String CATALOG_UNIT_PRECISION_LOCKED = "CATALOG_UNIT_PRECISION_LOCKED";
    public static final String CATALOG_VERSION_CONFLICT = "CATALOG_VERSION_CONFLICT";
    public static final String CATALOG_COVER_NOT_ELIGIBLE = "CATALOG_COVER_NOT_ELIGIBLE";

    private ErrorCodes() {
    }
}
