package com.zija.household.internal;

/**
 * household 模块错误码常量。
 * <p>
 * 仅本模块内使用；跨模块共享的错误码见 {@link com.zija.shared.ZijaErrorCodes}。
 */
public final class ErrorCodes {

    public static final String HOUSEHOLD_ALREADY_INITIALIZED = "HOUSEHOLD_ALREADY_INITIALIZED";
    public static final String HOUSEHOLD_INSUFFICIENT_ROLE = "HOUSEHOLD_INSUFFICIENT_ROLE";
    public static final String HOUSEHOLD_MEMBER_CONCURRENT_UPDATE = "HOUSEHOLD_MEMBER_CONCURRENT_UPDATE";
    public static final String HOUSEHOLD_MEMBER_NOT_ACTIVE = "HOUSEHOLD_MEMBER_NOT_ACTIVE";
    public static final String HOUSEHOLD_REQUEST_INVALID = "HOUSEHOLD_REQUEST_INVALID";
    public static final String HOUSEHOLD_STATE_CONFLICT = "HOUSEHOLD_STATE_CONFLICT";
    public static final String HOUSEHOLD_TOKEN_INVALID = "HOUSEHOLD_TOKEN_INVALID";

    private ErrorCodes() {
    }
}
