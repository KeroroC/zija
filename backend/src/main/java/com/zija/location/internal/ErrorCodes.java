package com.zija.location.internal;

/**
 * location 模块错误码常量。
 * <p>
 * 仅本模块内使用；跨模块共享的错误码见 {@link com.zija.shared.ZijaErrorCodes}。
 */
public final class ErrorCodes {

    public static final String LOCATION_CYCLE = "LOCATION_CYCLE";
    public static final String LOCATION_HAS_CHILDREN = "LOCATION_HAS_CHILDREN";
    public static final String LOCATION_REFERENCED = "LOCATION_REFERENCED";
    public static final String LOCATION_VERSION_CONFLICT = "LOCATION_VERSION_CONFLICT";

    private ErrorCodes() {
    }
}
