package com.zija.file.internal;

/**
 * file 模块错误码常量。
 * <p>
 * 仅本模块内使用；跨模块共享的错误码见 {@link com.zija.shared.ZijaErrorCodes}。
 */
public final class ErrorCodes {

    public static final String FILE_MEDIA_TYPE_UNSUPPORTED = "FILE_MEDIA_TYPE_UNSUPPORTED";
    public static final String FILE_SIGNATURE_MISMATCH = "FILE_SIGNATURE_MISMATCH";
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";

    private ErrorCodes() {
    }
}
