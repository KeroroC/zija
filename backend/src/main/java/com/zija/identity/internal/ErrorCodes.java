package com.zija.identity.internal;

/**
 * identity 模块错误码常量。
 * <p>
 * 仅本模块内使用；跨模块共享的错误码见 {@link com.zija.shared.ZijaErrorCodes}。
 */
public final class ErrorCodes {

    public static final String AUTH_LOGIN_FAILED = "AUTH_LOGIN_FAILED";
    public static final String AUTH_LOGIN_RATE_LIMITED = "AUTH_LOGIN_RATE_LIMITED";
    public static final String IDENTITY_USERNAME_TAKEN = "IDENTITY_USERNAME_TAKEN";
    public static final String IDENTITY_VERSION_CONFLICT = "IDENTITY_VERSION_CONFLICT";

    private ErrorCodes() {
    }
}
