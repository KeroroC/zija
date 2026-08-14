package com.zija.shared;

import com.zija.ZijaRequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * RFC 7807 Problem Details 共享工厂。
 * <p>
 * 统一构建携带 {@code errorCode} 与 {@code requestId} 属性的错误响应体，
 * 供各模块的异常处理器与安全处理器复用，避免各自拼装。
 */
public final class ZijaProblems {

    /** Problem Details 扩展属性名：稳定错误码。 */
    public static final String PROP_ERROR_CODE = "errorCode";

    /** Problem Details 扩展属性名：请求追踪 ID。 */
    public static final String PROP_REQUEST_ID = "requestId";

    /** Problem Details 扩展属性名：逐字段校验错误。 */
    public static final String PROP_FIELD_ERRORS = "fieldErrors";

    private ZijaProblems() {
    }

    /** 构建标题与详情相同的 Problem Details。 */
    public static ProblemDetail of(
            HttpServletRequest request,
            HttpStatus status,
            String title,
            String errorCode
    ) {
        return of(request, status, title, title, errorCode);
    }

    /** 构建指定标题与详情的 Problem Details。 */
    public static ProblemDetail of(
            HttpServletRequest request,
            HttpStatus status,
            String title,
            String detail,
            String errorCode
    ) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty(PROP_ERROR_CODE, errorCode);
        problem.setProperty(PROP_REQUEST_ID,
                request.getAttribute(ZijaRequestIdFilter.ATTRIBUTE));
        return problem;
    }
}
