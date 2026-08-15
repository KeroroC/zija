package com.zija.catalog.internal.exception;

/**
 * 附件没有资格被指定为封面：不在该物品上、已进回收站或媒体类型不是 JPEG/PNG/WebP。
 */
public class CatalogCoverNotEligibleException extends RuntimeException {

    public CatalogCoverNotEligibleException(String message) {
        super(message);
    }
}
