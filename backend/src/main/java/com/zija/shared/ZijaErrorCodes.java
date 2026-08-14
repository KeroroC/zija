package com.zija.shared;

/**
 * 跨模块共享的错误码常量。
 * <p>
 * 各模块自身专属的错误码定义在各模块的 {@code ErrorCodes} 常量类中，
 * 仅当同一错误码被两个以上模块（或根包组件）使用时才放入本类。
 */
public final class ZijaErrorCodes {

    /** 请求字段校验失败（根包校验处理器与 inventory 共用）。 */
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    /** 事件处理异常信息缺失时的兜底错误名（reminder 与 reporting 共用）。 */
    public static final String UNKNOWN_ERROR = "UnknownError";

    private ZijaErrorCodes() {
    }
}
