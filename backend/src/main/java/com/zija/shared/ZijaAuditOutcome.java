package com.zija.shared;

/**
 * 审计事件结果常量。
 * <p>
 * 适用于所有模块写入审计日志时的 {@code outcome} 字段，
 * 取值域由 system 模块的 AuditEvent 校验。
 */
public final class ZijaAuditOutcome {

    /** 操作成功完成。 */
    public static final String SUCCESS = "SUCCESS";

    /** 操作尝试后失败。 */
    public static final String FAILURE = "FAILURE";

    private ZijaAuditOutcome() {
    }
}
