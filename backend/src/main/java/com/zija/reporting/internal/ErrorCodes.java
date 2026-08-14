package com.zija.reporting.internal;

/**
 * reporting 模块错误码常量。
 * <p>
 * 仅本模块内使用；跨模块共享的错误码见 {@link com.zija.shared.ZijaErrorCodes}。
 */
public final class ErrorCodes {

    public static final String REPORTING_EXPORT_TOO_LARGE = "REPORTING_EXPORT_TOO_LARGE";
    public static final String REPORTING_INVALID_REQUEST = "REPORTING_INVALID_REQUEST";

    private ErrorCodes() {
    }
}
